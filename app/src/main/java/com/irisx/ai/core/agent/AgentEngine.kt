package com.irisx.ai.core.agent

import android.content.Context
import com.irisx.ai.data.SettingsStore

/**
 * Decision order:
 *  1. Local Hinglish intent parser  (offline, instant)
 *  2. Local small-talk answers      (offline)
 *  3. Cloud LLM with tool calling   (only if online + key + not localOnly)
 */
class AgentEngine(
    context: Context,
    private val settings: SettingsStore
) {

    private val appContext = context.applicationContext
    private val registry = ToolRegistry(appContext)
    private val llm = LlmClient(settings)

    suspend fun handle(utterance: String, online: Boolean): AgentReply {
        // 1. Offline intents first — zero latency, zero network.
        LocalIntentParser.parse(utterance)?.let { call ->
            val result = registry.execute(call)
            return AgentReply(result.message, result.ok, call.name, usedCloud = false)
        }

        // 2. Offline small talk.
        LocalSmallTalk.answer(utterance)?.let {
            return AgentReply(it, true, "small_talk", usedCloud = false)
        }

        // 3. Cloud fallback.
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
        "Main offline mode me hoon. Device ke kaam bol do — jaise 'WhatsApp kholo', 'Mummy ko call karo', '7 baje ka alarm', 'torch on', 'note karo…'."
    } else {
        "I'm running offline. Try a device command like 'open WhatsApp', 'call Mom', 'set alarm 7 am', 'torch on' or 'note down…'."
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
                "Calls, SMS, WhatsApp, apps, alarms, timers, torch, volume, media, notes, " +
                    "notifications aur screen control — sab offline."
            Regex("^(bye|good night|shubh ratri|so ja)").containsMatchIn(t) ->
                "Theek hai, main standby me jaa raha hoon."
            else -> null
        }
    }
}
