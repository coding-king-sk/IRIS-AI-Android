package com.irisx.ai.core.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.irisx.ai.data.SettingsStore

/**
 * Offline wake-word loop ("hey iris" by default).
 *
 * The first version restarted recognition instantly on every error, which on
 * most phones turns into a hot loop that the system recognizer refuses to
 * serve (ERROR_RECOGNIZER_BUSY) — so the mic looked alive but heard nothing.
 * This version paces the loop, backs off on repeated hard failures and lets
 * SttEngine fall back to the online recognizer when no offline pack exists.
 */
class WakeWordEngine(
    context: Context,
    private val settings: SettingsStore
) {

    private val stt = SttEngine(context)
    private val main = Handler(Looper.getMainLooper())
    private var running = false
    private var paused = false
    private var failures = 0
    private var onDetected: (() -> Unit)? = null
    private var onStatus: ((String) -> Unit)? = null
    private val pending = Runnable { cycle() }

    val micGranted: Boolean get() = stt.micGranted

    fun start(onDetected: () -> Unit, onStatus: (String) -> Unit = {}) {
        this.onDetected = onDetected
        this.onStatus = onStatus
        if (running) return
        if (!stt.micGranted) {
            onStatus("MIC PERMISSION NEEDED")
            return
        }
        if (!stt.recognizerAvailable) {
            onStatus("NO SPEECH ENGINE")
            return
        }
        running = true
        paused = false
        failures = 0
        schedule(200L)
    }

    fun pause() {
        paused = true
        main.removeCallbacks(pending)
        stt.cancel()
    }

    fun resume() {
        if (!running) return
        paused = false
        failures = 0
        schedule(400L)
    }

    fun stop() {
        running = false
        paused = false
        main.removeCallbacks(pending)
        stt.destroy()
    }

    private fun schedule(delayMs: Long) {
        main.removeCallbacks(pending)
        if (!running || paused) return
        main.postDelayed(pending, delayMs)
    }

    private fun cycle() {
        if (!running || paused) return
        onStatus?.invoke("LISTENING FOR WAKE WORD")
        stt.listen(
            preferOffline = true,
            onResult = { heard ->
                failures = 0
                if (matches(heard)) {
                    onStatus?.invoke("WAKE WORD DETECTED")
                    onDetected?.invoke()
                } else {
                    schedule(GAP_MS)
                }
            },
            onError = { reason ->
                // Silence / no-match is the normal case; only real faults back off.
                val soft = reason == "SILENCE" || reason == "NO MATCH" || reason == "NOTHING HEARD"
                if (soft) {
                    failures = 0
                    schedule(GAP_MS)
                } else {
                    failures++
                    if (reason == "MIC PERMISSION") {
                        onStatus?.invoke("MIC PERMISSION NEEDED")
                        running = false
                        return@listen
                    }
                    if (failures >= MAX_FAILURES) {
                        onStatus?.invoke("WAKE WORD PAUSED · " + reason)
                        running = false
                        return@listen
                    }
                    onStatus?.invoke("RETRYING · " + reason)
                    schedule(BACKOFF_MS * failures)
                }
            }
        )
    }

    private fun matches(heard: String): Boolean {
        val text = heard.lowercase().replace("[^a-z ]".toRegex(), " ").trim()
        val wake = settings.wakeWord.lowercase()
        if (wake.isNotBlank() && text.contains(wake)) return true
        // Tolerate common mis-hearings of "hey iris".
        val aliases = listOf(
            "hey iris", "hey irish", "hi iris", "a iris", "hey iras",
            "hey eris", "hey iris ai", "iris", "irish"
        )
        return aliases.any { text.contains(it) }
    }

    private companion object {
        const val GAP_MS = 700L
        const val BACKOFF_MS = 2500L
        const val MAX_FAILURES = 4
    }
}
