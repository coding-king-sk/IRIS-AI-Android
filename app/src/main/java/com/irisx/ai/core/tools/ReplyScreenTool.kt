package com.irisx.ai.core.tools

import android.content.Context
import com.irisx.ai.core.agent.IrisTool
import com.irisx.ai.core.agent.LlmClient
import com.irisx.ai.core.agent.ToolResult
import com.irisx.ai.core.automation.Automator
import com.irisx.ai.data.SettingsStore
import kotlinx.coroutines.runBlocking

/**
 * Screen-aware chaining: read the conversation that is on screen and write a
 * suitable reply straight into the app's text box.
 */
class ReplyScreenTool : IrisTool {
    override val name = "reply_screen"
    override val description =
        "Read the chat visible on screen and type a suitable reply into the message box."
    override val params = mapOf(
        "hint" to "Optional: what the reply should say",
        "send" to "yes to also tap send"
    )

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        if (!Automator.available()) return ToolResult(false, Automator.UNAVAILABLE)

        val screen = Automator.screenText().trim()
        if (screen.isBlank()) {
            return ToolResult(false, "Screen par padhne layak kuch nahi mila.")
        }

        val hint = args["hint"].orEmpty().trim()
        val settings = SettingsStore(context)
        val reply = when {
            hint.isNotBlank() -> hint
            else -> cloudReply(settings, screen) ?: localReply(screen)
        }

        if (!Automator.typeFirst(reply)) {
            return ToolResult(false, "Text box nahi mila. Reply ye hota: " + reply)
        }

        val wantsSend = args["send"].orEmpty().lowercase() in setOf("yes", "true", "bhejo", "send")
        val sent = if (wantsSend) {
            Automator.sleep(400)
            Automator.tapSend()
        } else {
            false
        }

        return ToolResult(
            true,
            if (sent) "Reply bhej diya: " + reply
            else "Reply likh diya: " + reply + " — send dabana baaki hai."
        )
    }

    private fun cloudReply(settings: SettingsStore, screen: String): String? {
        if (settings.localOnly || settings.apiKey.isBlank()) return null
        val prompt = buildString {
            append("Neeche ek phone screen ka text hai (chat ya message). ")
            append("Aakhri incoming message ka ek chhota, natural reply likho. ")
            append("Sirf reply text do, koi explanation nahi.\n\n")
            append(screen.take(1200))
        }
        val decision = runCatching {
            runBlocking { LlmClient(settings).decide(prompt, emptyList()) }
        }.getOrNull()
        return (decision as? LlmClient.Decision.Speak)
            ?.text
            ?.trim()
            ?.trim('"')
            ?.takeIf { it.isNotBlank() }
    }

    /** Offline fallback: simple intent matching on the last visible lines. */
    private fun localReply(screen: String): String {
        val tail = screen
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .takeLast(4)
            .joinToString(" ")
            .lowercase()

        return when {
            tail.contains("kaise ho") || tail.contains("how are you") ->
                "Main theek hoon, tum sunao?"
            tail.contains("kahan ho") || tail.contains("where are you") ->
                "Bas nikal raha hoon, thodi der me pahunchta hoon."
            tail.contains("call") || tail.contains("baat") ->
                "Abhi thoda busy hoon, thodi der me call karta hoon."
            tail.contains("thank") || tail.contains("shukriya") ->
                "Koi baat nahi!"
            tail.contains("?") ->
                "Haan, main dekh ke batata hoon."
            else -> "Theek hai, samajh gaya."
        }
    }
}
