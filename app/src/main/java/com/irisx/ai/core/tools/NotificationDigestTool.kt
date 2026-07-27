package com.irisx.ai.core.tools

import android.content.Context
import com.irisx.ai.core.agent.IrisTool
import com.irisx.ai.core.agent.ToolResult
import com.irisx.ai.service.IrisNotificationListener

/**
 * "Kya miss kiya" -> grouped summary of recent notifications per app,
 * built entirely on device from the notification listener ring buffer.
 */
class NotificationDigestTool : IrisTool {
    override val name = "notification_digest"
    override val description = "Summarise recent notifications grouped by app"

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val recent = IrisNotificationListener.recent()
        if (recent.isEmpty()) {
            return ToolResult(
                true,
                "Kuch miss nahi kiya. Agar notification access off hai to Settings me on kar do."
            )
        }

        val grouped = LinkedHashMap<String, MutableList<String>>()
        recent.forEach { line ->
            val index = line.indexOf(": ")
            val app = if (index > 0) line.substring(0, index) else "Other"
            val body = if (index > 0) line.substring(index + 2) else line
            grouped.getOrPut(app) { ArrayList() }.add(body)
        }

        val parts = ArrayList<String>()
        grouped.entries.take(6).forEach { entry ->
            val count = entry.value.size
            val head = entry.value.firstOrNull().orEmpty().take(70)
            val label = if (count > 1) {
                entry.key + " me " + count.toString() + ": " + head
            } else {
                entry.key + ": " + head
            }
            parts.add(label)
        }

        val total = recent.size
        return ToolResult(
            true,
            "Total " + total.toString() + " notifications. " + parts.joinToString(". ")
        )
    }
}
