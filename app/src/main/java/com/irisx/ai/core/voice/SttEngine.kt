package com.irisx.ai.core.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.irisx.ai.data.SettingsStore

/**
 * Speech-to-text.
 *
 * Two engines, in order:
 *  1. Vosk — true offline, no Google app, no network. Used whenever the model
 *     has been downloaded and the offline engine is switched on.
 *  2. Android's SpeechRecognizer — the fallback.
 *
 * Hard-won details this class handles, because they were the reason the mic
 * felt "dead" earlier:
 *  - RECORD_AUDIO can be denied at runtime; we surface that instead of failing
 *    silently.
 *  - EXTRA_PREFER_OFFLINE fails with a network error on phones that have no
 *    offline language pack installed, so after a couple of failures we stop
 *    forcing offline and let the online recognizer answer.
 *  - Errors 12/13 (language not supported / language unavailable) mean the
 *    recognizer has no en-IN pack. Asking for en-IN again will never work, so
 *    we retry once with no language extras at all — the device's own default —
 *    and remember that for the rest of the session.
 *  - A session can die without ever calling back; a watchdog frees the engine
 *    so the next tap always works.
 *  - `busy` used to swallow taps forever if a session leaked. Now a new request
 *    cancels the stale one instead of being dropped.
 */
class SttEngine(context: Context) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val settings = SettingsStore(appContext)
    private val vosk by lazy { VoskEngine(appContext) }
    private var recognizer: SpeechRecognizer? = null
    private var busy = false
    private var watchdog: Runnable? = null

    /** Set once the offline pack proves missing, so we stop fighting it. */
    private var offlineFailures = 0
    private var forceOnline = false

    /** Set once the requested language proves unavailable on this phone. */
    private var useDeviceLanguage = false

    var onAmplitude: ((Float) -> Unit)? = null

    val micGranted: Boolean
        get() = appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    val recognizerAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    /** True when the bundled offline engine is installed and switched on. */
    val voskActive: Boolean
        get() = settings.voskEnabled && vosk.isReady

    fun listen(
        preferOffline: Boolean = true,
        languageTag: String = "en-IN",
        onPartial: (String) -> Unit = {},
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        attempt: Int = 0
    ) {
        if (!micGranted) {
            onError("MIC PERMISSION")
            return
        }

        // Preferred path: fully offline Vosk.
        if (voskActive) {
            if (busy) cancel()
            val started = vosk.listenOnce(
                onPartial = { text -> main.post { onPartial(text) } },
                onText = { text ->
                    main.post {
                        onAmplitude?.invoke(0f)
                        if (text.isBlank()) onError("NOTHING HEARD") else onResult(text)
                    }
                },
                onFail = { label ->
                    main.post {
                        onAmplitude?.invoke(0f)
                        onError(label)
                    }
                }
            )
            if (started) return
            // Vosk could not start (mic busy, bad model) — fall through.
        }

        if (!recognizerAvailable) {
            onError("NO RECOGNIZER")
            return
        }
        // A stale session must never block a fresh request.
        if (busy) cancel()
        busy = true

        val useOffline = preferOffline && !forceOnline && !useDeviceLanguage

        main.post {
            var done = false
            val sr = recognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext).also {
                recognizer = it
            }

            fun finish(fail: String?, text: String?) {
                if (done) return
                done = true
                busy = false
                clearWatchdog()
                onAmplitude?.invoke(0f)
                if (fail != null) onError(fail) else onResult(text.orEmpty())
            }

            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) {
                    val level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    onAmplitude?.invoke(level)
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    onAmplitude?.invoke(0f)
                }

                override fun onError(error: Int) {
                    // 12 = ERROR_LANGUAGE_NOT_SUPPORTED, 13 = ERROR_LANGUAGE_UNAVAILABLE
                    // (literals so we stay compatible with minSdk 29).
                    val languageIssue = error == 12 || error == 13
                    if (languageIssue) {
                        useDeviceLanguage = true
                        forceOnline = true
                        runCatching { recognizer?.destroy() }
                        recognizer = null
                        if (attempt == 0) {
                            // Retry immediately with the phone's own language.
                            done = true
                            busy = false
                            clearWatchdog()
                            main.postDelayed(
                                {
                                    listen(
                                        preferOffline = false,
                                        languageTag = languageTag,
                                        onPartial = onPartial,
                                        onResult = onResult,
                                        onError = onError,
                                        attempt = 1
                                    )
                                },
                                RETRY_MS
                            )
                            return
                        }
                    }

                    val networkish = error == SpeechRecognizer.ERROR_NETWORK ||
                        error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                        error == SpeechRecognizer.ERROR_SERVER
                    if (useOffline && networkish) {
                        offlineFailures++
                        if (offlineFailures >= 2) forceOnline = true
                    }
                    if (error == SpeechRecognizer.ERROR_CLIENT ||
                        error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                    ) {
                        // These leave the recognizer in a bad state; rebuild next time.
                        runCatching { recognizer?.destroy() }
                        recognizer = null
                    }
                    finish(errorLabel(error), null)
                }

                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (text.isEmpty()) finish("NOTHING HEARD", null) else finish(null, text)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?.let(onPartial)
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            val started = runCatching {
                sr.startListening(buildIntent(useOffline, languageTag))
            }.isSuccess

            if (!started) {
                runCatching { sr.destroy() }
                recognizer = null
                finish("START FAILED", null)
                return@post
            }

            val w = Runnable {
                runCatching { recognizer?.cancel() }
                finish("SILENCE", null)
            }
            watchdog = w
            main.postDelayed(w, WATCHDOG_MS)
        }
    }

    fun cancel() {
        busy = false
        clearWatchdog()
        runCatching { vosk.stop() }
        main.post { runCatching { recognizer?.cancel() } }
    }

    fun destroy() {
        busy = false
        clearWatchdog()
        runCatching { vosk.stop() }
        main.post {
            runCatching { recognizer?.destroy() }
            recognizer = null
        }
    }

    /** Let the user retry offline recognition after installing a language pack. */
    fun resetOfflinePreference() {
        offlineFailures = 0
        forceOnline = false
        useDeviceLanguage = false
    }

    private fun clearWatchdog() {
        watchdog?.let { main.removeCallbacks(it) }
        watchdog = null
    }

    private fun buildIntent(preferOffline: Boolean, languageTag: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            if (!useDeviceLanguage) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            }
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                1500L
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                1500L
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
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
        SpeechRecognizer.ERROR_SERVER -> "SERVER ERROR"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "SILENCE"
        12, 13 -> "LANGUAGE PACK MISSING"
        else -> "STT ERROR " + code.toString()
    }

    private companion object {
        const val WATCHDOG_MS = 12000L
        const val RETRY_MS = 350L
    }
}
