package com.irisx.ai.core.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.irisx.ai.core.agent.IrisTool
import com.irisx.ai.core.agent.ToolResult
import com.irisx.ai.core.automation.Automator

/**
 * Instagram DM: "insta pe rahul ko message bhejo hi".
 *
 * ig.me deep links open the chat directly when the username is right. When the
 * user says a display name instead, we open Instagram's DM inbox, search the
 * name and open the first chat — all through the accessibility service.
 */
class InstagramSendTool : IrisTool {
    override val name = "instagram_send"
    override val description = "Open an Instagram chat and send a direct message."
    override val params = mapOf(
        "user" to "Instagram username or the person's name",
        "message" to "Message text"
    )

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val who = args["user"].orEmpty().trim()
        val message = args["message"].orEmpty().trim()
        if (who.isBlank()) return ToolResult(false, "Instagram pe kisko bhejna hai?")

        if (!AppLauncher.installed(context, "com.instagram.android")) {
            return ToolResult(false, "Instagram is phone me install nahi hai.")
        }

        val handle = who.replace(" ", "").lowercase()
        val opened = context.startExternal(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://ig.me/m/" + handle))
        ) || AppLauncher.launch(context, "com.instagram.android")

        if (!opened) return ToolResult(false, "Instagram khul nahi paya.")

        if (!Automator.available()) {
            return ToolResult(
                true,
                "Instagram khol diya. Auto-typing ke liye Accessibility on karo, warna khud likh do."
            )
        }

        Automator.waitForApp("com.instagram.android", 10000)
        Automator.sleep(2500)

        // If the deep link did not land in a chat, try the DM inbox route.
        if (message.isNotBlank() && !Automator.typeFirst(message)) {
            Automator.click("direct", "messages", "messenger", "chats")
            Automator.sleep(1600)
            Automator.click("search", "search input")
            Automator.sleep(900)
            Automator.typeFirst(who)
            Automator.sleep(1800)
            Automator.click(who)
            Automator.sleep(1800)
            if (!Automator.typeFirst(message)) {
                return ToolResult(
                    false,
                    "Instagram khul gaya par chat box nahi mila. Chat kholke bolo, main likh dunga."
                )
            }
        }

        if (message.isBlank()) return ToolResult(true, "Instagram chat khol diya.")

        Automator.sleep(600)
        val sent = Automator.tapSend()
        return if (sent) {
            ToolResult(true, who + " ko Instagram pe message bhej diya.")
        } else {
            ToolResult(true, "Message type ho gaya — bas send dabana baaki hai.")
        }
    }
}

/** Telegram message by username: "telegram pe rahul ko message bhejo". */
class TelegramSendTool : IrisTool {
    override val name = "telegram_send"
    override val description = "Open a Telegram chat and type a message."
    override val params = mapOf(
        "user" to "Telegram username or name",
        "message" to "Message text"
    )

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val who = args["user"].orEmpty().trim()
        val message = args["message"].orEmpty().trim()
        if (who.isBlank()) return ToolResult(false, "Telegram pe kisko bhejna hai?")
        if (!AppLauncher.installed(context, "org.telegram.messenger")) {
            return ToolResult(false, "Telegram install nahi hai.")
        }

        val opened = context.startExternal(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/" + who.replace(" ", "")))
        ) || AppLauncher.launch(context, "org.telegram.messenger")
        if (!opened) return ToolResult(false, "Telegram khul nahi paya.")

        if (message.isBlank() || !Automator.available()) {
            return ToolResult(true, "Telegram khol diya.")
        }

        Automator.waitForApp("org.telegram", 9000)
        Automator.sleep(2200)
        if (!Automator.typeFirst(message)) {
            return ToolResult(false, "Chat box nahi mila. Chat khol ke dobara bolo.")
        }
        Automator.sleep(500)
        val sent = Automator.tapSend()
        return ToolResult(
            true,
            if (sent) who + " ko Telegram pe message bhej diya."
            else "Message likh diya, send dabana baaki hai."
        )
    }
}
