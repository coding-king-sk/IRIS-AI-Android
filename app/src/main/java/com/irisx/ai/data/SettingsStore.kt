package com.irisx.ai.data

import android.content.Context
import android.content.SharedPreferences

/** Plain SharedPreferences — zero extra deps, works fully offline. */
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

    private companion object {
        const val KEY_WAKE = "wake_word"
        const val KEY_API = "api_key"
        const val KEY_URL = "base_url"
        const val KEY_MODEL = "model"
        const val KEY_OFFLINE_FIRST = "offline_first"
        const val KEY_LOCAL_ONLY = "local_only"
        const val KEY_TTS = "tts_enabled"
        const val KEY_HINGLISH = "hinglish"
        const val KEY_RATE = "speech_rate"
    }
}
