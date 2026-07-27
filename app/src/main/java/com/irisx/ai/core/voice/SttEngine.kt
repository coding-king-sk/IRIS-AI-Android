package com.irisx.ai.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Speech-to-text using Android's on-device recognizer.
 * `EXTRA_PREFER_OFFLINE` keeps recognition local when the language pack is
 * installed, so command capture keeps working in airplane mode.
 */
class SttEngine(context: Context) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var busy = false

    var onAmplitude: ((Float) -> Unit)? = null

    fun listen(
        preferOffline: Boolean = true,
        languageTag: String = "en-IN",
        onPartial: (String) -> Unit = {},
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onError("NO RECOGNIZER")
            return
        }
        if (busy) return
        busy = true

        main.post {
            recognizer?.destroy()
            val sr = SpeechRecognizer.createSpeechRecognizer(appContext)
            recognizer = sr

            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) {
                    // Map roughly -2..10 dB onto 0..1 for the AI core animation.
                    val level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    onAmplitude?.invoke(level)
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    onAmplitude?.invoke(0f)
                }

                override fun onError(error: Int) {
                    busy = false
                    onAmplitude?.invoke(0f)
                    onError(errorLabel(error))
                }

                override fun onResults(results: Bundle?) {
                    busy = false
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (text.isEmpty()) onError("NOTHING HEARD") else onResult(text)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.let(onPartial)
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            sr.startListening(buildIntent(preferOffline, languageTag))
        }
    }

    fun cancel() {
        busy = false
        main.post { runCatching { recognizer?.cancel() } }
    }

    fun destroy() {
        busy = false
        main.post {
            runCatching { recognizer?.destroy() }
            recognizer = null
        }
    }

    private fun buildIntent(preferOffline: Boolean, languageTag: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                1200L
            )
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        }

    private fun errorLabel(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "AUDIO ERROR"
        SpeechRecognizer.ERROR_CLIENT -> "CLIENT ERROR"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "MIC PERMISSION"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "OFFLINE PACK MISSING"
        SpeechRecognizer.ERROR_NO_MATCH -> "NO MATCH"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RECOGNIZER BUSY"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "SILENCE"
        else -> "STT ERROR " + code.toString()
    }
}
