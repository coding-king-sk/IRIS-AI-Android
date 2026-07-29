package com.irisx.ai.core.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.irisx.ai.data.SettingsStore

/**
 * Wake-word loop ("hey iris" by default).
 *
 * Two very different modes:
 *  - **Vosk stream (preferred):** the mic stays open in one continuous session,
 *    so there is no beep and no mic-icon blinking, and nothing is missed between
 *    cycles.
 *  - **System recognizer (fallback):** Android only gives short sessions, so the
 *    loop has to restart — that is what made the mic flash on/off and swallow
 *    words. Here it is paced slowly and it gives up with a clear message instead
 *    of hot-looping forever.
 */
class WakeWordEngine(
    context: Context,
    private val settings: SettingsStore
) {

    private val stt = SttEngine(context)
    private val vosk = VoskEngine(context)
    private val main = Handler(Looper.getMainLooper())
    private var running = false
    private var paused = false
    private var streaming = false
    private var failures = 0
    private var onDetected: (() -> Unit)? = null
    private var onStatus: ((String) -> Unit)? = null
    private val pending = Runnable { cycle() }

    val micGranted: Boolean get() = stt.micGranted

    /** True when the smooth, no-beep offline listener is in use. */
    val continuousOffline: Boolean
        get() = settings.voskEnabled && vosk.isReady

    fun start(onDetected: () -> Unit, onStatus: (String) -> Unit = {}) {
        this.onDetected = onDetected
        this.onStatus = onStatus
        if (running) return
        if (!stt.micGranted) {
            onStatus("MIC PERMISSION CHAHIYE")
            return
        }
        running = true
        paused = false
        failures = 0

        if (startStream()) return

        if (!stt.recognizerAvailable) {
            onStatus("KOI SPEECH ENGINE NAHI")
            running = false
            return
        }
        onStatus("WAKE WORD ON (offline model se behtar chalega)")
        schedule(300L)
    }

    fun pause() {
        paused = true
        main.removeCallbacks(pending)
        stopStream()
        stt.cancel()
    }

    fun resume() {
        if (!running) return
        paused = false
        failures = 0
        if (startStream()) return
        schedule(600L)
    }

    fun stop() {
        running = false
        paused = false
        main.removeCallbacks(pending)
        stopStream()
        stt.destroy()
    }

    // ---- Vosk continuous mode ---------------------------------------------

    private fun startStream(): Boolean {
        if (!continuousOffline || streaming) return streaming
        val started = vosk.startStream(
            onPartial = { heard -> if (matches(heard)) trigger() },
            onText = { heard -> if (matches(heard)) trigger() },
            onFail = {
                streaming = false
                onStatus?.invoke("OFFLINE SUNNA RUK GAYA — dobara chalu kar raha hoon")
                schedule(1500L)
            }
        )
        streaming = started
        if (started) onStatus?.invoke("SUN RAHA HOON (offline, bina beep)")
        return started
    }

    private fun stopStream() {
        if (streaming) {
            streaming = false
            runCatching { vosk.stop() }
        }
    }

    private fun trigger() {
        if (!running || paused) return
        onStatus?.invoke("WAKE WORD MILA")
        // Free the mic before the command capture starts.
        pause()
        onDetected?.invoke()
    }

    // ---- System recognizer fallback ---------------------------------------

    private fun schedule(delayMs: Long) {
        main.removeCallbacks(pending)
        if (!running || paused || streaming) return
        main.postDelayed(pending, delayMs)
    }

    private fun cycle() {
        if (!running || paused) return
        if (startStream()) return
        onStatus?.invoke("WAKE WORD SUN RAHA HOON")
        stt.listen(
            preferOffline = true,
            onResult = { heard ->
                failures = 0
                if (matches(heard)) trigger() else schedule(GAP_MS)
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
                        onStatus?.invoke("MIC PERMISSION CHAHIYE")
                        running = false
                        return@listen
                    }
                    if (failures >= MAX_FAILURES) {
                        onStatus?.invoke(
                            "WAKE WORD BAND (" + reason + ") — bolo 'offline voice setup karo', " +
                                "phir bina beep ke chalega"
                        )
                        running = false
                        return@listen
                    }
                    onStatus?.invoke("DOBARA KOSHISH · " + reason)
                    schedule(BACKOFF_MS * failures)
                }
            }
        )
    }

    private fun matches(heard: String): Boolean {
        val text = heard.lowercase().replace("[^a-z ]".toRegex(), " ").trim()
        if (text.isBlank()) return false
        val wake = settings.wakeWord.lowercase()
        if (wake.isNotBlank() && text.contains(wake)) return true
        // Tolerate common mis-hearings of "hey iris".
        val aliases = listOf(
            "hey iris", "hey irish", "hi iris", "a iris", "hey iras",
            "hey eris", "hey iris ai", "iris", "irish", "eris", "araise"
        )
        return aliases.any { text.contains(it) }
    }

    private companion object {
        // Slower pacing: the old 700 ms gap made the mic icon blink constantly.
        const val GAP_MS = 1400L
        const val BACKOFF_MS = 3000L
        const val MAX_FAILURES = 3
    }
}
