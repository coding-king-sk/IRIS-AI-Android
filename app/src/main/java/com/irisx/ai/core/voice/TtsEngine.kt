package com.irisx.ai.core.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.irisx.ai.data.SettingsStore
import java.util.Locale

/**
 * Speech output with two engines.
 *
 *  1. [NeuralTts] - an on-device VITS model. This is the "IRIS ki apni awaaz":
 *     same voice on every phone, works offline, no Google app needed. Used
 *     whenever the model is downloaded and the setting is on.
 *  2. The phone's own TextToSpeech - always available fallback. We still pick
 *     the best installed English voice (en-IN > en-GB > en-US, non compact),
 *     drop the pitch a little and add a breath between sentences so it does
 *     not sound like a navigation robot.
 */
class TtsEngine(context: Context) {

    private val appContext = context.applicationContext
    private val settings = SettingsStore(appContext)

    private var ready = false
    private val pending = ArrayDeque<Pair<String, Float>>()
    private var doneCallback: (() -> Unit)? = null

    /** Name of the voice actually in use - handy for Settings/diagnostics. */
    var voiceLabel: String = "SYSTEM DEFAULT"
        private set

    private val tts = TextToSpeech(appContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            applyIrisVoice()
            while (pending.isNotEmpty()) {
                val (text, rate) = pending.removeFirst()
                enqueue(text, rate)
            }
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                doneCallback?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                doneCallback?.invoke()
            }
        })
    }

    /** True when the downloaded neural voice is switched on and present. */
    fun neuralReady(): Boolean =
        settings.nttsEnabled && NeuralTts.installed(appContext)

    /** Pick the best available English voice and set the assistant tone. */
    private fun applyIrisVoice() {
        runCatching { tts.language = Locale("en", "IN") }
        runCatching {
            var best: Voice? = null
            var bestScore = Int.MIN_VALUE
            for (v in tts.voices.orEmpty()) {
                if (!v.locale.language.equals("en", ignoreCase = true)) continue
                var score = when (v.locale.country.uppercase(Locale.ROOT)) {
                    "IN" -> 120
                    "GB" -> 80
                    "US" -> 60
                    "AU" -> 40
                    else -> 10
                }
                // QUALITY_VERY_HIGH = 500 ... QUALITY_VERY_LOW = 100
                score += v.quality
                // Network voices sound best but go silent without data.
                if (v.isNetworkConnectionRequired) score -= 120
                val name = v.name.lowercase(Locale.ROOT)
                // Google's non-compact variants are the natural sounding ones.
                if (name.contains("-x-")) score += 40
                if (name.contains("compact") || name.contains("espeak")) score -= 80
                if (v.features.contains("notInstalled")) continue
                if (score > bestScore) {
                    bestScore = score
                    best = v
                }
            }
            best?.let {
                tts.voice = it
                voiceLabel = it.name
            }
        }
        runCatching { tts.setPitch(PITCH) }
    }

    fun speak(text: String, rate: Float = 1.0f, onDone: () -> Unit = {}) {
        doneCallback = onDone
        val clean = sanitize(text)
        if (clean.isBlank()) {
            onDone()
            return
        }
        if (neuralReady()) {
            val started = NeuralTts.speak(appContext, clean, rate * RATE_TRIM, onDone)
            if (started) {
                voiceLabel = "IRIS NEURAL (VITS)"
                return
            }
        }
        if (!ready) {
            pending.addLast(clean to rate)
            return
        }
        enqueue(clean, rate)
    }

    private fun enqueue(text: String, rate: Float) {
        runCatching { tts.setPitch(PITCH) }
        // A touch slower than the caller asks for - reads calmer, less robotic.
        tts.setSpeechRate((rate * RATE_TRIM).coerceIn(0.5f, 2.0f))
        val id = "iris-" + System.nanoTime().toString()
        val parts = text.split(Regex("(?<=[.!?\u0964])\\s+")).filter { it.isNotBlank() }
        if (parts.size <= 1) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            return
        }
        parts.forEachIndexed { index, part ->
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts.speak(part, mode, null, id + "-" + index.toString())
            if (index < parts.size - 1) {
                runCatching {
                    tts.playSilentUtterance(BREATH_MS, TextToSpeech.QUEUE_ADD, null)
                }
            }
        }
    }

    /** Spoken text should not contain markdown, emoji or bullet characters. */
    private fun sanitize(raw: String): String = raw
        .replace(Regex("""```[\s\S]*?```"""), " ")
        .replace(Regex("""\[(.*?)]\(.*?\)"""), "$1")
        .replace(Regex("""[*_`#>|~]"""), " ")
        .replace(Regex("""^\s*[-\u2022]\s*""", RegexOption.MULTILINE), " ")
        .replace(Regex("""[\uD800-\uDBFF][\uDC00-\uDFFF]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

    fun stop() {
        runCatching { NeuralTts.stop() }
        runCatching { tts.stop() }
    }

    fun shutdown() {
        runCatching { NeuralTts.stop() }
        runCatching {
            tts.stop()
            tts.shutdown()
        }
    }

    private companion object {
        const val PITCH = 0.94f
        const val RATE_TRIM = 0.95f
        const val BREATH_MS = 140L
    }
}
