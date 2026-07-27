package com.irisx.ai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.irisx.ai.core.agent.AgentEngine
import com.irisx.ai.core.voice.SttEngine
import com.irisx.ai.core.voice.TtsEngine
import com.irisx.ai.core.voice.WakeWordEngine
import com.irisx.ai.data.HistoryStore
import com.irisx.ai.data.SettingsStore
import com.irisx.ai.service.IrisForegroundService
import com.irisx.ai.util.Feedback
import com.irisx.ai.util.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Role { USER, IRIS, SYSTEM }

enum class VisionMode { OFF, CAMERA, SCREEN }

data class ChatLine(
    val role: Role,
    val text: String,
    val at: Long = System.currentTimeMillis()
)

data class IrisUiState(
    val isConnected: Boolean = false,
    val isMuted: Boolean = false,
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val networkOnline: Boolean = false,
    val visionMode: VisionMode = VisionMode.OFF,
    val amplitude: Float = 0f,
    val status: String = "STANDBY",
    val engineMode: String = "LOCAL",
    val lastTool: String? = null,
    val continuous: Boolean = false,
    val transcript: List<ChatLine> = emptyList()
)

/**
 * Owns the whole voice loop:
 *   wake word (offline) -> STT (offline-first) -> agent (local intents, cloud
 *   fallback) -> tool execution (offline) -> TTS (on-device).
 *
 * With continuous mode on, the mic reopens right after IRIS finishes speaking,
 * so a conversation can continue without repeating the wake word.
 */
class AssistantViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsStore(app)
    private val history = HistoryStore(app)
    private val network = NetworkMonitor(app)
    private val feedback = Feedback(app)

    private val tts = TtsEngine(app)
    private val stt = SttEngine(app)
    private val agent = AgentEngine(app, settings)
    private val wakeWord = WakeWordEngine(app, settings)

    /** Consecutive follow-up turns without a wake word. */
    private var followUps = 0

    private val _state = MutableStateFlow(
        IrisUiState(transcript = history.recent().map { ChatLine(Role.SYSTEM, it) })
    )
    val state: StateFlow<IrisUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            network.online.collect { online ->
                _state.update {
                    it.copy(
                        networkOnline = online,
                        engineMode = if (online && settings.apiKey.isNotBlank()) "CLOUD" else "LOCAL"
                    )
                }
            }
        }
        stt.onAmplitude = { level ->
            _state.update { it.copy(amplitude = level) }
        }
    }

    fun toggleConnection() {
        if (_state.value.isConnected) shutdown() else boot()
    }

    private fun boot() {
        _state.update { it.copy(isConnected = true, status = "CORE ONLINE") }
        if (settings.haptics) feedback.doubleTick()
        say("IRIS online. Bolo, main sun raha hoon.")
        IrisForegroundService.start(getApplication())
        wakeWord.start(
            onDetected = { if (!_state.value.isMuted) startListening(false) },
            onStatus = { s -> _state.update { it.copy(status = s) } }
        )
    }

    private fun shutdown() {
        wakeWord.stop()
        stt.cancel()
        tts.stop()
        followUps = 0
        IrisForegroundService.stop(getApplication())
        _state.update {
            it.copy(
                isConnected = false,
                isListening = false,
                isSpeaking = false,
                amplitude = 0f,
                continuous = false,
                visionMode = VisionMode.OFF,
                status = "STANDBY"
            )
        }
    }

    fun toggleMic() {
        val muted = !_state.value.isMuted
        _state.update {
            it.copy(
                isMuted = muted,
                continuous = false,
                status = if (muted) "MIC MUTED" else "LISTENING FOR WAKE WORD"
            )
        }
        if (settings.haptics) feedback.tick()
        if (muted) {
            stt.cancel()
            wakeWord.stop()
        } else if (_state.value.isConnected) {
            wakeWord.start(
                onDetected = { startListening(false) },
                onStatus = { s -> _state.update { it.copy(status = s) } }
            )
        }
    }

    fun setVisionMode(mode: VisionMode) {
        _state.update { it.copy(visionMode = mode) }
    }

    /** Manual (typed) command — works with the mic fully disabled. */
    fun submitText(text: String) {
        if (text.isBlank()) return
        followUps = 0
        handleUtterance(text.trim(), spoken = false)
    }

    fun stopSpeaking() {
        tts.stop()
        _state.update { it.copy(isSpeaking = false, continuous = false) }
    }

    private fun startListening(fromFollowUp: Boolean) {
        if (_state.value.isListening) return
        if (!fromFollowUp) followUps = 0
        wakeWord.pause()
        if (settings.haptics) feedback.tick()
        if (settings.soundCues) feedback.wakeCue()
        _state.update {
            it.copy(
                isListening = true,
                continuous = fromFollowUp,
                status = if (fromFollowUp) "FOLLOW UP" else "LISTENING"
            )
        }
        stt.listen(
            preferOffline = settings.offlineFirst,
            onResult = { text ->
                _state.update { it.copy(isListening = false) }
                handleUtterance(text, spoken = true)
            },
            onError = { message ->
                _state.update {
                    it.copy(isListening = false, continuous = false, status = message)
                }
                followUps = 0
                if (settings.soundCues && !fromFollowUp) feedback.errorCue()
                wakeWord.resume()
            }
        )
    }

    private fun handleUtterance(text: String, spoken: Boolean) {
        append(ChatLine(Role.USER, text))
        _state.update { it.copy(status = "THINKING") }
        viewModelScope.launch {
            val reply = agent.handle(text, _state.value.networkOnline)
            append(ChatLine(Role.IRIS, reply.text))
            history.log(text + " -> " + (reply.toolName ?: "chat"))
            _state.update {
                it.copy(
                    lastTool = reply.toolName,
                    engineMode = if (reply.usedCloud) "CLOUD" else "LOCAL",
                    status = if (reply.ok) "DONE" else "FAILED"
                )
            }
            if (settings.haptics) feedback.tick(if (reply.ok) 20L else 60L)
            if (settings.soundCues) {
                if (reply.ok) feedback.doneCue() else feedback.errorCue()
            }
            val shouldFollowUp = spoken &&
                settings.continuousMode &&
                _state.value.isConnected &&
                !_state.value.isMuted &&
                followUps < MAX_FOLLOW_UPS
            say(reply.text) {
                if (shouldFollowUp) {
                    followUps++
                    startListening(true)
                } else {
                    followUps = 0
                    _state.update { it.copy(continuous = false) }
                    wakeWord.resume()
                }
            }
        }
    }

    private fun say(text: String, onFinished: (() -> Unit)? = null) {
        if (!settings.ttsEnabled) {
            onFinished?.invoke()
            return
        }
        _state.update { it.copy(isSpeaking = true) }
        tts.speak(
            text = text,
            rate = settings.speechRate,
            onDone = {
                _state.update { it.copy(isSpeaking = false, amplitude = 0f) }
                onFinished?.invoke()
            }
        )
    }

    private fun append(line: ChatLine) {
        _state.update { it.copy(transcript = (it.transcript + line).takeLast(80)) }
    }

    override fun onCleared() {
        wakeWord.stop()
        stt.destroy()
        tts.shutdown()
        network.stop()
        super.onCleared()
    }

    private companion object {
        const val MAX_FOLLOW_UPS = 3
    }
}
