package com.irisx.ai.core.reminders

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Reminder(val id: String, val text: String, val at: Long)

/** Reminders live in a plain JSON file, so they work with zero network. */
class ReminderStore(context: Context) {

    private val file = File(context.filesDir, "iris_reminders.json")

    fun all(): List<Reminder> {
        if (!file.exists()) return emptyList()
        val raw = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        val out = ArrayList<Reminder>()
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                out.add(
                    Reminder(
                        item.optString("id"),
                        item.optString("text"),
                        item.optLong("at")
                    )
                )
            }
        }
        return out.sortedBy { it.at }
    }

    fun upcoming(): List<Reminder> {
        val floor = System.currentTimeMillis() - 60000L
        return all().filter { it.at >= floor }
    }

    fun add(text: String, at: Long): Reminder {
        val reminder = Reminder("rem-" + System.nanoTime().toString(), text, at)
        save(all() + reminder)
        return reminder
    }

    fun delete(id: String) {
        save(all().filter { it.id != id })
    }

    fun prune() {
        val cutoff = System.currentTimeMillis() - 86400000L
        save(all().filter { it.at >= cutoff })
    }

    private fun save(list: List<Reminder>) {
        val array = JSONArray()
        list.forEach { reminder ->
            val item = JSONObject()
            item.put("id", reminder.id)
            item.put("text", reminder.text)
            item.put("at", reminder.at)
            array.put(item)
        }
        runCatching { file.writeText(array.toString()) }
    }
}
