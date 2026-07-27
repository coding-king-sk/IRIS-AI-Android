package com.irisx.ai.core.tools

import android.content.Context
import com.irisx.ai.core.agent.IrisTool
import com.irisx.ai.core.agent.ToolResult
import com.irisx.ai.service.IrisAccessibilityService
import com.irisx.ai.service.IrisNotificationListener

class ScreenActionTool : IrisTool {
    override val name = "screen"
    override val description = "Perform a global screen gesture: back, home, recents or scroll."
    override val params = mapOf("action" to "back, home, recents, scroll or notifications")

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val service = IrisAccessibilityService.instance
            ?: return ToolResult(
                false,
                "Screen control ke liye Settings me Accessibility permission on karni hogi."
            )
        val action = args["action"].orEmpty().lowercase()
        val ok = service.perform(action)
        return if (ok) ToolResult(true, "Ho gaya: $action.")
        else ToolResult(false, "'$action' perform nahi ho paya.")
    }
}

class ReadScreenTool : IrisTool {
    override val name = "read_screen"
    override val description = "Read the visible text on the current screen."

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val service = IrisAccessibilityService.instance
            ?: return ToolResult(
                false,
                "Screen padhne ke liye Accessibility permission chahiye."
            )
        val text = service.readScreenText().trim()
        return if (text.isEmpty()) {
            ToolResult(true, "Screen par padhne layak text nahi mila.")
        } else {
            ToolResult(true, text.take(600))
        }
    }
}

class ReadNotificationsTool : IrisTool {
    override val name = "read_notifications"
    override val description = "Read the most recent notifications."

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val items = IrisNotificationListener.recent()
        if (items.isEmpty()) {
            return ToolResult(
                true,
                "Koi nayi notification nahi hai (ya notification access on nahi hai)."
            )
        }
        return ToolResult(true, items.take(4).joinToString(" · "))
    }
}
