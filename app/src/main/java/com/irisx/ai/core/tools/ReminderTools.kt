package com.irisx.ai.core.tools

import android.content.Context
import com.irisx.ai.core.agent.IrisTool
import com.irisx.ai.core.agent.ToolResult
import com.irisx.ai.core.reminders.ReminderScheduler
import com.irisx.ai.core.reminders.ReminderStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReminderAddTool : IrisTool {
    override val name = "reminder"
    override val description =
        "Set an offline reminder that notifies at a clock time or after a delay"
    override val params = mapOf(
        "text" to "What to remind about",
        "hour" to "Clock hour in 24h format, optional",
        "minute" to "Clock minute, optional",
        "in_minutes" to "Minutes from now, optional"
    )

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val rawText = (args["text"] ?: args["query"] ?: "").trim()
        val text = if (rawText.isEmpty()) "Reminder" else rawText

        val delay = args["in_minutes"]?.trim()?.toIntOrNull()
        val hour = args["hour"]?.trim()?.toIntOrNull()
        val minute = args["minute"]?.trim()?.toIntOrNull() ?: 0

        val cal = Calendar.getInstance()
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        when {
            delay != null && delay > 0 -> cal.add(Calendar.MINUTE, delay)
            hour != null -> {
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                if (cal.timeInMillis <= System.currentTimeMillis()) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            else -> return ToolResult(false, "Kab yaad dilana hai? Time ya minute batao")
        }

        val store = ReminderStore(context)
        val reminder = store.add(text, cal.timeInMillis)
        val scheduled = ReminderScheduler.schedule(context, reminder)
        if (!scheduled) {
            store.delete(reminder.id)
            return ToolResult(false, "Reminder set nahi ho paya")
        }
        val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
        return ToolResult(
            true,
            "Theek hai, " + formatter.format(Date(reminder.at)) + " par yaad dila dunga: " + text
        )
    }
}

class ReminderListTool : IrisTool {
    override val name = "read_reminders"
    override val description = "List the upcoming reminders"

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val items = ReminderStore(context).upcoming()
        if (items.isEmpty()) return ToolResult(true, "Koi reminder pending nahi hai")
        val formatter = SimpleDateFormat("d MMM h:mm a", Locale.getDefault())
        val lines = items.take(8).map { formatter.format(Date(it.at)) + " " + it.text }
        return ToolResult(true, "Pending reminders: " + lines.joinToString(", "))
    }
}
