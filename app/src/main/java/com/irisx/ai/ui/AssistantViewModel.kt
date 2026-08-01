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
    val liveMode: Boolean = false,
    val transcript: List<ChatLine> = emptyList()
)

/**
 * Owns the whole voice loop:
 *   wake word -> STT -> agent (local intents, cloud fallback) -> tools -> TTS.
 *
 * Live mode makes it feel like a call: the mic reopens after every answer with
 * no wake word, and the wake detector stays armed while IRIS is talking so the
 * user can cut in mid-sentence.
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
        IrisUiState(
            liveMode = settings.liveMode,
            transcript = history.recent().map { ChatLine(Role.SYSTEM, it) }
        )
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
            // Smooth the mic level a little so the orb waveform flows.
            _state.update { it.copy(amplitude = it.amplitude * 0.55f + level * 0.45f) }
        }
    }

    fun toggleConnection() {
        if (_state.value.isConnected) shutdown() else boot()
    }

    /** Live-call style conversation on/off. */
    fun toggleLive() {
        val live = !_state.value.liveMode
        settings.liveMode = live
        _state.update { it.copy(liveMode = live, status = if (live) "LIVE MODE" else "NORMAL MODE") }
        if (live && !_state.value.isConnected) boot()
    }

    private fun boot() {
        _state.update { it.copy(isConnected = true, status = "CORE ONLINE") }
        if (settings.haptics) feedback.doubleTick()
        say(LocalGreeting.pick())
        IrisForegroundService.start(getApplication())
        wakeWord.start(
            onDetected = { onWake() },
            onStatus = { s -> _state.update { it.copy(status = s) } }
        )
    }

    /** Wake word can also arrive while IRIS is mid-sentence — that is barge-in. */
    private fun onWake() {
        if (_state.value.isMuted) return
        if (_state.value.isSpeaking) {
            tts.stop()
            _state.update { it.copy(isSpeaking = false) }
        }
        startListening(false)
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
                onDetected = { onWake() },
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
        if (settings.soundCues && !fromFollowUp) feedback.wakeCue()
        _state.update {
            it.copy(
                isListening = true,
                continuous = fromFollowUp,
                status = when {
                    _state.value.liveMode -> "LIVE \u00b7 BOLO"
                    fromFollowUp -> "FOLLOW UP"
                    else -> "LISTENING"
                }
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
            val live = _state.value.liveMode
            val shouldFollowUp = spoken &&
                (live || settings.continuousMode) &&
                _state.value.isConnected &&
                !_state.value.isMuted &&
                (live || followUps < MAX_FOLLOW_UPS)
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

/** Casual openers so IRIS does not greet the same way every single time. */
private object LocalGreeting {
    private val lines = listOf(
        "Haan bhai, bolo. Main sun raha hoon.",
        "IRIS online. Kya karna hai?",
        "Aa gaya main. Bolo kya kaam hai?"
    )

    fun pick(): String = lines.random()
}
