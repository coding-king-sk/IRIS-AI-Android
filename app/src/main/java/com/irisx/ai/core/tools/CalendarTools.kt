package com.irisx.ai.core.tools

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.irisx.ai.core.agent.IrisTool
import com.irisx.ai.core.agent.ToolResult
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Creates a calendar event through the system insert intent (no permission needed). */
class CalendarAddTool : IrisTool {
    override val name = "calendar_add"
    override val description = "Create a calendar event at a given time"
    override val params = mapOf(
        "title" to "Event title",
        "hour" to "Start hour in 24h format",
        "minute" to "Start minute",
        "day_offset" to "0 for today, 1 for tomorrow"
    )

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val rawTitle = (args["title"] ?: "").trim()
        val title = if (rawTitle.isEmpty()) "IRIS event" else rawTitle
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, args["day_offset"]?.toIntOrNull() ?: 0)
        cal.set(Calendar.HOUR_OF_DAY, args["hour"]?.toIntOrNull() ?: 9)
        cal.set(Calendar.MINUTE, args["minute"]?.toIntOrNull() ?: 0)
        cal.set(Calendar.SECOND, 0)
        val start = cal.timeInMillis

        val intent = Intent(Intent.ACTION_INSERT)
        intent.data = CalendarContract.Events.CONTENT_URI
        intent.putExtra(CalendarContract.Events.TITLE, title)
        intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
        intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 3600000L)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return runCatching {
            context.startActivity(intent)
            ToolResult(true, "Calendar khol diya, event save kar do: " + title)
        }.getOrElse { ToolResult(false, "Calendar app nahi mila") }
    }
}

/** Reads today's events from the calendar provider. */
class CalendarTodayTool : IrisTool {
    override val name = "calendar_today"
    override val description = "Read today's calendar events"

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return ToolResult(false, "Calendar padhne ki permission nahi hai")

        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        val from = cal.timeInMillis
        val to = from + 86400000L

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, from)
        ContentUris.appendId(builder, to)
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN
        )
        val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
        val out = ArrayList<String>()

        runCatching {
            context.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                CalendarContract.Instances.BEGIN + " ASC"
            )?.use { cursor ->
                while (cursor.moveToNext() && out.size < 8) {
                    val title = cursor.getString(0) ?: "Untitled"
                    val begin = cursor.getLong(1)
                    out.add(formatter.format(Date(begin)) + " " + title)
                }
            }
        }

        if (out.isEmpty()) return ToolResult(true, "Aaj calendar me kuch nahi hai")
        return ToolResult(true, "Aaj ke events: " + out.joinToString(", "))
    }
}
