package com.irisx.ai.core.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** On-device Text-to-Speech — no network required. */
class TtsEngine(context: Context) {

    private var ready = false
    private val pending = ArrayDeque<Pair<String, Float>>()
    private var doneCallback: (() -> Unit)? = null

    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            while (pending.isNotEmpty()) {
                val (text, rate) = pending.removeFirst()
                enqueue(text, rate)
            }
        }
    }.apply {
        runCatching { language = Locale("en", "IN") }
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

    fun speak(text: String, rate: Float = 1.0f, onDone: () -> Unit = {}) {
        doneCallback = onDone
        if (!ready) {
            pending.addLast(text to rate)
            return
        }
        enqueue(text, rate)
    }

    private fun enqueue(text: String, rate: Float) {
        tts.setSpeechRate(rate)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "iris-${'$'}{System.nanoTime()}")
    }

    fun stop() {
        runCatching { tts.stop() }
    }

    fun shutdown() {
        runCatching {
            tts.stop()
            tts.shutdown()
        }
    }
}
