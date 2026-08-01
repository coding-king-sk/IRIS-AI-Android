package com.irisx.ai.core.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.irisx.ai.core.agent.IrisTool
import com.irisx.ai.core.agent.ToolResult

/**
 * "Instagram pe reels chalao" / "shorts dikhao".
 *
 * Opening the reels feed is a normal deep link, so it always works. Moving to
 * the NEXT reel is a swipe, and only the Accessibility service can do that for
 * you, so we say so honestly instead of pretending.
 */
class ReelsTool : IrisTool {
    override val name = "reels"
    override val description = "Instagram reels ya YouTube shorts kholo aur scroll karo"
    override val params: Map<String, String> = mapOf(
        "app" to "instagram | youtube",
        "action" to "open | next"
    )

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val app = (args["app"] ?: "").lowercase()
        val action = (args["action"] ?: "open").lowercase()
        val youtube = app.contains("youtube") || app.contains("yt") || app.contains("short")

        if (action.contains("next") || action.contains("scroll") || action.contains("agla")) {
            return ToolResult(
                true,
                "Agli reel ke liye upar swipe chahiye \u2014 wo sirf Accessibility on hone par ho paata hai. " +
                    "Settings me \"IRIS Screen Control\" chalu kar do, phir bolo: scroll karo."
            )
        }

        val uri = if (youtube) {
            Uri.parse("https://www.youtube.com/shorts")
        } else {
            Uri.parse("https://www.instagram.com/reels/")
        }
        val pkg = if (youtube) "com.google.android.youtube" else "com.instagram.android"

        val direct = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val opened = runCatching { context.startActivity(direct) }.isSuccess
        if (opened) {
            return ToolResult(
                true,
                if (youtube) "YouTube Shorts khol diya." else "Instagram Reels khol diya. Upar swipe karte jao."
            )
        }

        val fallback = context.packageManager.getLaunchIntentForPackage(pkg)
        if (fallback != null) {
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val ok = runCatching { context.startActivity(fallback) }.isSuccess
            if (ok) return ToolResult(true, "App khol diya \u2014 reels tab pe tap kar lo.")
        }

        return ToolResult(false, "Ye app phone me mila nahi.")
    }
}
