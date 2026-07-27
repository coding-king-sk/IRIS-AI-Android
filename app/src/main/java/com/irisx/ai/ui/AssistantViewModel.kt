package com.irisx.ai.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.irisx.ai.core.agent.AgentEngine
import com.irisx.ai.core.voice.SttEngine
import com.irisx.ai.core.voice.TtsEngine
import com.irisx.ai.core.voice.WakeWordEngine
import com.irisx.ai.data.HistoryStore
import com.irisx.ai.data.SettingsStore
import com.irisx.ai.service.IrisForegroundService
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
    val transcript: List<ChatLine> = emptyList()
)

/**
 * Owns the whole voice loop:
 *   wake word (offline) -> STT (offline-first) -> agent (local intents, cloud
 *   fallback) -> tool execution (offline) -> TTS (on-device).
 */
class AssistantViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsStore(app)
    private val history = HistoryStore(app)
    private val network = NetworkMonitor(app)

    private val tts = TtsEngine(app)
    private val stt = SttEngine(app)
    private val agent = AgentEngine(app, settings)
    private val wakeWord = WakeWordEngine(app, settings)

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
        say("IRIS online. Bolo, main sun raha hoon.")
        IrisForegroundService.start(getApplication())
        wakeWord.start(
            onDetected = { if (!_state.value.isMuted) startListening() },
            onStatus = { s -> _state.update { it.copy(status = s) } }
        )
    }

    private fun shutdown() {
        wakeWord.stop()
        stt.cancel()
        tts.stop()
        IrisForegroundService.stop(getApplication())
        _state.update {
            it.copy(
                isConnected = false,
                isListening = false,
                isSpeaking = false,
                amplitude = 0f,
                visionMode = VisionMode.OFF,
                status = "STANDBY"
            )
        }
    }

    fun toggleMic() {
        val muted = !_state.value.isMuted
        _state.update { it.copy(isMuted = muted, status = if (muted) "MIC MUTED" else "LISTENING FOR WAKE WORD") }
        if (muted) {
            stt.cancel()
            wakeWord.stop()
        } else if (_state.value.isConnected) {
            wakeWord.start(
                onDetected = { startListening() },
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
        handleUtterance(text.trim())
    }

    fun stopSpeaking() {
        tts.stop()
        _state.update { it.copy(isSpeaking = false) }
    }

    private fun startListening() {
        if (_state.value.isListening) return
        wakeWord.pause()
        _state.update { it.copy(isListening = true, status = "LISTENING") }
        stt.listen(
            preferOffline = settings.offlineFirst,
            onResult = { text ->
                _state.update { it.copy(isListening = false) }
                handleUtterance(text)
            },
            onError = { message ->
                _state.update { it.copy(isListening = false, status = message) }
                wakeWord.resume()
            }
        )
    }

    private fun handleUtterance(text: String) {
        append(ChatLine(Role.USER, text))
        _state.update { it.copy(status = "THINKING") }
        viewModelScope.launch {
            val reply = agent.handle(text, _state.value.networkOnline)
            append(ChatLine(Role.IRIS, reply.text))
            history.log("${'$'}text -> ${'$'}{reply.toolName ?: "chat"}")
            _state.update {
                it.copy(
                    lastTool = reply.toolName,
                    engineMode = if (reply.usedCloud) "CLOUD" else "LOCAL",
                    status = if (reply.ok) "DONE" else "FAILED"
                )
            }
            say(reply.text)
            wakeWord.resume()
        }
    }

    private fun say(text: String) {
        if (!settings.ttsEnabled) return
        _state.update { it.copy(isSpeaking = true) }
        tts.speak(
            text = text,
            rate = settings.speechRate,
            onDone = { _state.update { it.copy(isSpeaking = false, amplitude = 0f) } }
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
}
