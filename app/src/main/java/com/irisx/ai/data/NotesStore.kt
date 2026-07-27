package com.irisx.ai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Note(val id: String, val text: String, val createdAt: Long)

/** Offline note vault backed by a JSON file in app-private storage. */
class NotesStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, "iris_notes.json")

    fun all(): List<Note> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Note(
                    id = o.getString("id"),
                    text = o.getString("text"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                )
            }.sortedByDescending { it.createdAt }
        }.getOrDefault(emptyList())
    }

    fun add(text: String): Note {
        val note = Note(
            id = System.nanoTime().toString(),
            text = text,
            createdAt = System.currentTimeMillis()
        )
        persist(all() + note)
        return note
    }

    fun delete(id: String) = persist(all().filterNot { it.id == id })

    fun search(query: String): List<Note> =
        all().filter { it.text.contains(query, ignoreCase = true) }

    private fun persist(notes: List<Note>) {
        val array = JSONArray()
        notes.forEach { n ->
            array.put(
                JSONObject()
                    .put("id", n.id)
                    .put("text", n.text)
                    .put("createdAt", n.createdAt)
            )
        }
        runCatching { file.writeText(array.toString()) }
    }
}
