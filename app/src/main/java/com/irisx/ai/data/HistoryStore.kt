package com.irisx.ai.data

import android.content.Context

/** Rolling command log, kept locally for the dashboard feed. */
class HistoryStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("iris_history", Context.MODE_PRIVATE)

    fun recent(limit: Int = 10): List<String> =
        (prefs.getString(KEY, "") ?: "")
            .split("\n")
            .filter { it.isNotBlank() }
            .takeLast(limit)

    fun log(entry: String) {
        val merged = (recent(40) + entry).joinToString("\n")
        prefs.edit().putString(KEY, merged).apply()
    }

    private companion object {
        const val KEY = "log"
    }
}
