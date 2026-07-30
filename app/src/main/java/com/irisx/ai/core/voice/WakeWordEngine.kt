package com.irisx.ai.core.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.irisx.ai.data.SettingsStore

/**
 * Wake-word loop.
 *
 * Three modes, best first:
 *  - **openWakeWord (neural, preferred):** a few MB of ONNX models score the mic
 *    stream continuously. No beep, no mic blinking, no language pack, and it
 *    behaves exactly the same online and offline.
 *  - **Vosk stream:** full offline recognizer, also continuous, but a 40 MB
 *    model and much heavier than a wake-word classifier needs to be.
 *  - **System recognizer (last resort):** Android only gives short sessions, so
 *    the loop has to restart — that is what made the mic flash on/off and
 *    swallow words. Here it is paced slowly and gives up with a clear message
 *    instead of hot-looping forever.
 */
class WakeWordEngine(
    context: Context,
    private val settings: SettingsStore
) {

    private val stt = SttEngine(context)
    private val vosk = VoskEngine(context)
    private val oww = OpenWakeWord(context)
    private val main = Handler(Looper.getMainLooper())
    private var running = false
    private var paused = false
    private var streaming = false
    private var neural = false
    private var failures = 0
    private var onDetected: (() -> Unit)? = null
    private var onStatus: ((String) -> Unit)? = null
    private val pending = Runnable { cycle() }

    val micGranted: Boolean get() = stt.micGranted

    /** True when a smooth, no-beep listener is available. */
    val continuousOffline: Boolean
        get() = (settings.owwEnabled && oww.isReady) || (settings.voskEnabled && vosk.isReady)

    /** True when the neural detector is the one in use. */
    val neuralActive: Boolean get() = neural

    /** The phrase the user should say for the neural model in use. */
    val neuralPhrase: String get() = oww.spokenPhrase

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

        if (startNeural()) return
        if (startStream()) return

        if (!stt.recognizerAvailable) {
            onStatus("KOI SPEECH ENGINE NAHI")
            running = false
            return
        }
        onStatus("WAKE WORD ON (bolo 'neural wake word setup karo' — bina beep chalega)")
        schedule(300L)
    }

    fun pause() {
        paused = true
        main.removeCallbacks(pending)
        stopNeural()
        stopStream()
        stt.cancel()
    }

    fun resume() {
        if (!running) return
        paused = false
        failures = 0
        if (startNeural()) return
        if (startStream()) return
        schedule(600L)
    }

    fun stop() {
        running = false
        paused = false
        main.removeCallbacks(pending)
        stopNeural()
        stopStream()
        stt.destroy()
    }

    // ---- openWakeWord neural mode -----------------------------------------

    private fun startNeural(): Boolean {
        if (neural) return true
        if (!settings.owwEnabled || !oww.isReady) return false
        val ok = oww.start(
            onDetected = { _ -> main.post { trigger() } },
            onError = { reason ->
                main.post {
                    neural = false
                    onStatus?.invoke("NEURAL WAKE RUKA \u00b7 " + reason)
                    if (!startStream()) schedule(1500L)
                }
            }
        )
        neural = ok
        if (ok) {
            onStatus?.invoke("SUN RAHA HOON \u00b7 bolo \"" + oww.spokenPhrase + "\"")
        }
        return ok
    }

    private fun stopNeural() {
        if (neural) {
            neural = false
            runCatching { oww.stop() }
        }
    }

    // ---- Vosk continuous mode ---------------------------------------------

    private fun startStream(): Boolean {
        if (streaming) return true
        if (!settings.voskEnabled || !vosk.isReady) return false
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
        if (!running || paused || streaming || neural) return
        main.postDelayed(pending, delayMs)
    }

    private fun cycle() {
        if (!running || paused) return
        if (startNeural()) return
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
                            "WAKE WORD BAND (" + reason + ") — bolo 'neural wake word setup karo', " +
                                "phir bina beep ke chalega"
                        )
                        running = false
                        return@listen
                    }
                    onStatus?.invoke("DOBARA KOSHISH \u00b7 " + reason)
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
