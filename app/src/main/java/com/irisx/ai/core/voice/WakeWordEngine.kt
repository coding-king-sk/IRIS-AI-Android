package com.irisx.ai.core.voice

import android.content.Context
import com.irisx.ai.data.SettingsStore

/**
 * Offline wake-word loop ("hey iris" by default).
 *
 * Implementation note: this uses a short-utterance recognition loop with
 * EXTRA_PREFER_OFFLINE instead of a paid hotword SDK, so the build stays
 * dependency-free and works without any network. Swap `matches()` for a
 * Porcupine/openWakeWord detector later without touching the caller.
 */
class WakeWordEngine(
    context: Context,
    private val settings: SettingsStore
) {

    private val stt = SttEngine(context)
    private var running = false
    private var paused = false
    private var onDetected: (() -> Unit)? = null
    private var onStatus: ((String) -> Unit)? = null

    fun start(onDetected: () -> Unit, onStatus: (String) -> Unit = {}) {
        this.onDetected = onDetected
        this.onStatus = onStatus
        if (running) return
        running = true
        paused = false
        loop()
    }

    fun pause() {
        paused = true
        stt.cancel()
    }

    fun resume() {
        if (!running) return
        paused = false
        loop()
    }

    fun stop() {
        running = false
        paused = false
        stt.destroy()
    }

    private fun loop() {
        if (!running || paused) return
        onStatus?.invoke("LISTENING FOR WAKE WORD")
        stt.listen(
            preferOffline = true,
            onResult = { heard ->
                if (matches(heard)) {
                    onStatus?.invoke("WAKE WORD DETECTED")
                    onDetected?.invoke()
                } else {
                    loop()
                }
            },
            onError = {
                // Silence / no-match is the normal case: just keep cycling.
                loop()
            }
        )
    }

    private fun matches(heard: String): Boolean {
        val text = heard.lowercase().replace("[^a-z ]".toRegex(), " ").trim()
        val wake = settings.wakeWord.lowercase()
        if (text.contains(wake)) return true
        // Tolerate common mis-hearings of "hey iris".
        val aliases = listOf("hey iris", "hey irish", "hi iris", "a iris", "hey iras", "iris")
        return aliases.any { text.contains(it) }
    }
}
