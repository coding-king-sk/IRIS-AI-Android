package com.irisx.ai.core.agent

import android.content.Context
import com.irisx.ai.data.SettingsStore

/**
 * Decision order:
 *  1. Fast-lane intents (songs, messages, apps)  (offline, instant)
 *  2. Local Hinglish intent parser               (offline, instant)
 *  3. Local small-talk answers                   (offline)
 *  4. Cloud LLM with tool calling                (only if online + key + not localOnly)
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

        // 3. Offline small talk.
        LocalSmallTalk.answer(utterance)?.let {
            return AgentReply(it, true, "small_talk", usedCloud = false)
        }

        // 4. Cloud fallback.
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

    private fun offlineFallbackText(): String = if (settings.hinglishMode) {
        "Ye samajh nahi aaya. Aise bolo — 'YouTube kholo', 'koi bhi gana chalao', " +
            "'Riya ko whatsapp karo ki main aa raha hoon', 'insta pe rahul ko message bhejo hi', " +
            "'7 baje ka alarm', 'torch on'."
    } else {
        "I didn't catch that. Try 'open YouTube', 'play a song', " +
            "'whatsapp Riya saying I'm coming', 'set alarm 7 am' or 'torch on'."
    }
}

/** Tiny rule-based responder so the app is useful with no network at all. */
object LocalSmallTalk {
    fun answer(raw: String): String? {
        val t = raw.lowercase()
        return when {
            Regex("^(hi|hello|hey|namaste|salaam)\\b").containsMatchIn(t) ->
                "Namaste! IRIS ready hai. Kya karna hai?"
            t.contains("tumhara naam") || t.contains("your name") || t.contains("who are you") ->
                "Main IRIS hoon — aapke phone ka voice-first operating layer."
            t.contains("thank") || t.contains("shukriya") || t.contains("dhanyawad") ->
                "Koi baat nahi. Aur kuch?"
            t.contains("what can you do") || t.contains("kya kar sakt") ->
                "Gane chalana, WhatsApp/Instagram message, calls, apps, alarms, timers, torch, " +
                    "volume, notes, notifications aur screen control — zyada tar offline."
            Regex("^(bye|good night|shubh ratri|so ja)").containsMatchIn(t) ->
                "Theek hai, main standby me jaa raha hoon."
            else -> null
        }
    }
}
