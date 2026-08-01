package com.irisx.ai.core.agent

import android.content.Context
import com.irisx.ai.data.SettingsStore
import com.irisx.ai.service.IrisAccessibilityService
import kotlin.random.Random

/**
 * Decision order:
 *  1. Fast-lane intents (songs, messages, toggles, apps)  (offline, instant)
 *  2. Local Hinglish intent parser                        (offline, instant)
 *  3. Context shortcut: if a music app is in front, treat a short phrase as a
 *     song to search                                      (offline)
 *  4. Local small-talk / banter                           (offline)
 *  5. Cloud LLM with tool calling   (only if online + key + not localOnly)
 */
class AgentEngine(
    context: Context,
    private val settings: SettingsStore
) {

    private val appContext = context.applicationContext
    private val registry = ToolRegistry(appContext)
    private val llm = LlmClient(settings)

    suspend fun handle(utterance: String, online: Boolean): AgentReply {
        // 1 + 2. Offline intents first — zero latency, zero network.
        val call = ExtraIntentParser.parse(utterance) ?: LocalIntentParser.parse(utterance)
        if (call != null) {
            val result = registry.execute(call)
            return AgentReply(result.message, result.ok, call.name, usedCloud = false)
        }

        // 3. YouTube / Spotify already open? Then a bare phrase is a song.
        musicContextCall(utterance)?.let { musicCall ->
            val result = registry.execute(musicCall)
            return AgentReply(result.message, result.ok, musicCall.name, usedCloud = false)
        }

        // 4. Offline small talk.
        LocalSmallTalk.answer(utterance)?.let {
            return AgentReply(it, true, "small_talk", usedCloud = false)
        }

        // 5. Cloud fallback.
        val cloudAllowed = online && !settings.localOnly && settings.apiKey.isNotBlank()
        if (!cloudAllowed) {
            return AgentReply(
                text = offlineFallbackText(),
                ok = false,
                toolName = null,
                usedCloud = false
            )
        }

        return when (val decision = llm.decide(utterance, registry.tools)) {
            is LlmClient.Decision.Call -> {
                val result = registry.execute(decision.call)
                AgentReply(result.message, result.ok, decision.call.name, usedCloud = true)
            }
            is LlmClient.Decision.Speak -> AgentReply(decision.text, true, "chat", usedCloud = true)
            is LlmClient.Decision.Failed -> AgentReply(
                "Cloud brain se baat nahi ho payi (${decision.reason}). Local commands abhi bhi chalenge.",
                ok = false,
                usedCloud = true
            )
        }
    }

    /**
     * When YouTube / YouTube Music / Spotify is the app in front, a short phrase
     * that matched nothing else is almost always a song name — search it there
     * instead of saying "samajh nahi aaya".
     */
    private fun musicContextCall(utterance: String): ToolCall? {
        val text = utterance.trim()
        if (text.length < 2) return null
        val words = text.split(Regex("\\s+"))
        if (words.size > 8) return null
        if (text.contains("?")) return null

        val foreground = runCatching { IrisAccessibilityService.foregroundPackage }
            .getOrNull()
            ?.toString()
            .orEmpty()
            .lowercase()
        if (foreground.isBlank()) return null

        val app = when {
            foreground.contains("spotify") -> "spotify"
            foreground.contains("youtube.music") -> "youtube music"
            foreground.contains("youtube") -> "youtube"
            else -> return null
        }
        return ToolCall("play_music", mapOf("query" to text, "app" to app))
    }

    private fun offlineFallbackText(): String = if (settings.hinglishMode) {
        LocalSmallTalk.pick(
            "Ye wala samajh nahi aaya yaar. Aise bolo \u2014 'koi bhi gana chalao', " +
                "'Papa ko location bhejo', 'torch on', 'battery kitni hai'.",
            "Arre ye miss ho gaya. Try karo \u2014 'YouTube kholo', 'gana pause karo', " +
                "'silent mode on', 'Riya ko whatsapp karo ki aa raha hoon'."
        )
    } else {
        "I didn't catch that. Try 'open YouTube', 'play a song', " +
            "'whatsapp Riya saying I'm coming', 'set alarm 7 am' or 'torch on'."
    }
}

/**
 * Offline banter. The tone is deliberately dost-jaisi casual Hinglish — short,
 * warm, a bit cheeky — instead of a formal assistant voice.
 */
object LocalSmallTalk {

    fun pick(vararg options: String): String = options[Random.nextInt(options.size)]

    fun answer(raw: String): String? {
        val t = raw.lowercase().trim()
        return when {
            Regex("^(hi|hello|hey|namaste|salaam|oye|sun)\\b").containsMatchIn(t) -> pick(
                "Haan bhai, bolo!",
                "Arre wah, aa gaye. Kya karna hai?",
                "Bolo bolo, sun raha hoon."
            )
            Regex("kaise ho|kaisa hai|how are you|kya haal").containsMatchIn(t) -> pick(
                "Ekdum mast, battery full aur mood bhi. Tum sunao?",
                "Badhiya! Tum batao, kya chal raha hai?"
            )
            Regex("kya kar rah|what are you doing|busy ho").containsMatchIn(t) -> pick(
                "Tumhara wait kar raha tha, aur kya.",
                "Kuch nahi, bas idhar-udhar processes ghum raha hoon. Bolo?"
            )
            t.contains("tumhara naam") || t.contains("your name") || t.contains("who are you") ->
                "Main IRIS hoon \u2014 tumhare phone ka dost, jo bolo wo kar deta hoon."
            Regex("joke|mazak|hansao|kuch funny").containsMatchIn(t) -> pick(
                "Ek programmer ki shaadi nahi hui \u2014 kyunki uski life me hamesha 'commitment issues' the.",
                "Doctor: aapko aaram ki zarurat hai. Phone: main to hamesha charging pe rehta hoon.",
                "Maine WiFi se pucha \u2014 tum itne slow kyu ho? Bola: signal nahi, self-respect kam hai.",
                "Battery boli \u2014 mujhe 1% pe mat chhodo, mai bhi feelings rakhti hoon."
            )
            Regex("bore ho|boring|kuch batao|time pass").containsMatchIn(t) -> pick(
                "Chalo gana laga deta hoon \u2014 bolo 'koi bhi gana chalao'.",
                "Bore ho rahe ho? Ek joke sunau? Bas bolo 'joke sunao'."
            )
            Regex("i love you|tum acche ho|good job|shabash|badhiya").containsMatchIn(t) -> pick(
                "Arre arre, tareef kar rahe ho. Kaam bolo, turant karta hoon.",
                "Thank you yaar! Ab batao kya karna hai."
            )
            Regex("thank|shukriya|dhanyawad|thanks").containsMatchIn(t) -> pick(
                "Koi baat nahi yaar.",
                "Bas itni si baat! Aur kuch?"
            )
            Regex("kya kar sakt|what can you do|help|madad").containsMatchIn(t) ->
                "Gana chalana aur control karna, WhatsApp/Instagram message, call, apps kholna, " +
                    "alarm-timer, torch, silent/DND, screenshot, battery-time, location bhejna, " +
                    "notes aur screen padhna \u2014 zyada tar bina internet ke."
            Regex("^(bye|good night|shubh ratri|so ja|chal bye)").containsMatchIn(t) -> pick(
                "Theek hai, standby me ja raha hoon. Bulana ho to bas bol dena.",
                "Good night! Main yahin hoon, jab bhi bulao."
            )
            else -> null
        }
    }
}
