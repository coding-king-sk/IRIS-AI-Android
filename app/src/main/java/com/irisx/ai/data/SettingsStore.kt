package com.irisx.ai.data

import android.content.Context
import android.content.SharedPreferences

/** Plain SharedPreferences - zero extra deps, works fully offline. */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("iris_settings", Context.MODE_PRIVATE)

    var wakeWord: String
        get() = prefs.getString(KEY_WAKE, "hey iris") ?: "hey iris"
        set(value) = prefs.edit().putString(KEY_WAKE, value.lowercase()).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API, value.trim()).apply()

    var baseUrl: String
        get() = prefs.getString(KEY_URL, "https://api.openai.com/v1/chat/completions")
            ?: "https://api.openai.com/v1/chat/completions"
        set(value) = prefs.edit().putString(KEY_URL, value.trim()).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, "gpt-4o-mini") ?: "gpt-4o-mini"
        set(value) = prefs.edit().putString(KEY_MODEL, value.trim()).apply()

    /** Prefer the on-device recognizer before touching the network. */
    var offlineFirst: Boolean
        get() = prefs.getBoolean(KEY_OFFLINE_FIRST, true)
        set(value) = prefs.edit().putBoolean(KEY_OFFLINE_FIRST, value).apply()

    /** Use the bundled Vosk engine (true offline, no Google app needed). */
    var voskEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOSK, false)
        set(value) = prefs.edit().putBoolean(KEY_VOSK, value).apply()

    /**
     * Neural wake word (openWakeWord / ONNX). Fully on-device, so it behaves
     * identically whether the phone is online or offline.
     */
    var owwEnabled: Boolean
        get() = prefs.getBoolean(KEY_OWW, false)
        set(value) = prefs.edit().putBoolean(KEY_OWW, value).apply()

    /** File name (without extension) of the openWakeWord model in use. */
    var wakeModel: String
        get() = prefs.getString(KEY_OWW_MODEL, "hey_jarvis_v0.1") ?: "hey_jarvis_v0.1"
        set(value) = prefs.edit().putString(KEY_OWW_MODEL, value.trim()).apply()

    /**
     * On-device neural voice (VITS via ONNX Runtime). When off, or when the
     * model has not been downloaded, the phone's own TTS engine is used.
     */
    var nttsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NTTS, false)
        set(value) = prefs.edit().putBoolean(KEY_NTTS, value).apply()

    /**
     * Live-call style conversation: after every answer the mic reopens on its
     * own, and the wake word can cut IRIS off mid-sentence.
     */
    var liveMode: Boolean
        get() = prefs.getBoolean(KEY_LIVE, true)
        set(value) = prefs.edit().putBoolean(KEY_LIVE, value).apply()

    /** Hard airplane-mode for the brain: never call the cloud. */
    var localOnly: Boolean
        get() = prefs.getBoolean(KEY_LOCAL_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_LOCAL_ONLY, value).apply()

    var ttsEnabled: Boolean
        get() = prefs.getBoolean(KEY_TTS, true)
        set(value) = prefs.edit().putBoolean(KEY_TTS, value).apply()

    var hinglishMode: Boolean
        get() = prefs.getBoolean(KEY_HINGLISH, true)
        set(value) = prefs.edit().putBoolean(KEY_HINGLISH, value).apply()

    var speechRate: Float
        get() = prefs.getFloat(KEY_RATE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_RATE, value).apply()

    /**
     * After IRIS answers, keep the mic open for a short follow-up window so the
     * wake word does not have to be repeated for every turn.
     */
    var continuousMode: Boolean
        get() = prefs.getBoolean(KEY_CONTINUOUS, true)
        set(value) = prefs.edit().putBoolean(KEY_CONTINUOUS, value).apply()

    var haptics: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS, value).apply()

    var soundCues: Boolean
        get() = prefs.getBoolean(KEY_SOUND_CUES, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_CUES, value).apply()

    /** Live subtitle inside the floating bubble while IRIS listens/answers. */
    var bubbleSubtitle: Boolean
        get() = prefs.getBoolean(KEY_SUBTITLE, true)
        set(value) = prefs.edit().putBoolean(KEY_SUBTITLE, value).apply()

    /** Accent palette id: green, cyan, amber or violet. */
    var accent: String
        get() = prefs.getString(KEY_ACCENT, "green") ?: "green"
        set(value) = prefs.edit().putString(KEY_ACCENT, value).apply()

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()

    /** Voice-profile gating for the wake word (approximate, on-device only). */
    var voiceLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_LOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_LOCK, value).apply()

    var voiceProfileLevel: Float
        get() = prefs.getFloat(KEY_VOICE_LEVEL, 0f)
        set(value) = prefs.edit().putFloat(KEY_VOICE_LEVEL, value).apply()

    var voiceProfileSamples: Int
        get() = prefs.getInt(KEY_VOICE_SAMPLES, 0)
        set(value) = prefs.edit().putInt(KEY_VOICE_SAMPLES, value).apply()

    private companion object {
        const val KEY_WAKE = "wake_word"
        const val KEY_API = "api_key"
        const val KEY_URL = "base_url"
        const val KEY_MODEL = "model"
        const val KEY_OFFLINE_FIRST = "offline_first"
        const val KEY_VOSK = "vosk_enabled"
        const val KEY_OWW = "oww_enabled"
        const val KEY_OWW_MODEL = "oww_model"
        const val KEY_NTTS = "ntts_enabled"
        const val KEY_LIVE = "live_mode"
        const val KEY_LOCAL_ONLY = "local_only"
        const val KEY_TTS = "tts_enabled"
        const val KEY_HINGLISH = "hinglish"
        const val KEY_RATE = "speech_rate"
        const val KEY_CONTINUOUS = "continuous_mode"
        const val KEY_HAPTICS = "haptics"
        const val KEY_SOUND_CUES = "sound_cues"
        const val KEY_SUBTITLE = "bubble_subtitle"
        const val KEY_ACCENT = "accent"
        const val KEY_ONBOARDING = "onboarding_done"
        const val KEY_VOICE_LOCK = "voice_lock"
        const val KEY_VOICE_LEVEL = "voice_level"
        const val KEY_VOICE_SAMPLES = "voice_samples"
    }
}
