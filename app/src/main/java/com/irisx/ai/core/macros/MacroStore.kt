package com.irisx.ai.core.macros

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A user defined voice shortcut: one name, many commands. */
data class Macro(val name: String, val steps: List<String>)

/** Stores voice shortcuts on device as plain JSON. No cloud, no database. */
class MacroStore(private val context: Context) {

    private val file: File
        get() = File(context.filesDir, "iris_macros.json")

    fun all(): List<Macro> {
        if (!file.exists()) return emptyList()
        val text = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        val arr = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<Macro>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("name").trim()
            if (name.isBlank()) continue
            val stepsArr = obj.optJSONArray("steps") ?: JSONArray()
            val steps = mutableListOf<String>()
            for (j in 0 until stepsArr.length()) {
                val step = stepsArr.optString(j).trim()
                if (step.isNotBlank()) steps.add(step)
            }
            out.add(Macro(name, steps))
        }
        return out
    }

    fun find(name: String): Macro? {
        val q = name.trim().lowercase()
        if (q.isBlank()) return null
        val list = all()
        return list.firstOrNull { it.name.lowercase() == q }
            ?: list.firstOrNull { it.name.lowercase().contains(q) }
            ?: list.firstOrNull { q.contains(it.name.lowercase()) }
    }

    fun save(name: String, steps: List<String>) {
        val clean = name.trim()
        if (clean.isBlank()) return
        val kept = all().filter { !it.name.equals(clean, ignoreCase = true) }
        val arr = JSONArray()
        for (macro in kept + Macro(clean, steps)) {
            val obj = JSONObject()
            obj.put("name", macro.name)
            obj.put("steps", JSONArray(macro.steps))
            arr.put(obj)
        }
        runCatching { file.writeText(arr.toString()) }
    }

    fun delete(name: String): Boolean {
        val kept = all().filter { !it.name.equals(name.trim(), ignoreCase = true) }
        if (kept.size == all().size) return false
        val arr = JSONArray()
        for (macro in kept) {
            val obj = JSONObject()
            obj.put("name", macro.name)
            obj.put("steps", JSONArray(macro.steps))
            arr.put(obj)
        }
        runCatching { file.writeText(arr.toString()) }
        return true
    }
}
