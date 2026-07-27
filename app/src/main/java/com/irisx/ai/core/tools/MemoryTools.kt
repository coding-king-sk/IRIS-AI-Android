package com.irisx.ai.core.tools

import android.content.Context
import com.irisx.ai.core.agent.IrisTool
import com.irisx.ai.core.agent.ToolResult
import com.irisx.ai.data.HistoryStore
import com.irisx.ai.data.NotesStore
import java.util.Locale
import kotlin.math.ln

/**
 * Tiny on-device retrieval layer (a poor man's RAG): notes + command history are
 * scored with token overlap weighted by inverse document frequency. No model, no
 * embeddings, no network — works in airplane mode.
 */
class MemorySearchTool : IrisTool {
    override val name = "memory_search"
    override val description =
        "Search saved notes and past commands on device and return the best matches"
    override val params = mapOf("query" to "What to look for")

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val query = (args["query"] ?: args["text"] ?: "").trim()
        if (query.isEmpty()) return ToolResult(false, "Kya dhundhna hai?")

        val notes = runCatching { NotesStore(context).all().map { it.text } }.getOrDefault(emptyList())
        val history = runCatching { HistoryStore(context).recent(60) }.getOrDefault(emptyList())
        val docs = ArrayList<Pair<String, String>>()
        notes.forEach { docs.add(Pair("note", it)) }
        history.forEach { docs.add(Pair("history", it)) }
        if (docs.isEmpty()) return ToolResult(true, "Abhi kuch notes ya history save nahi hai")

        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return ToolResult(false, "Query samajh nahi aayi")

        val docTokens = docs.map { tokenize(it.second) }
        val total = docs.size.toDouble()
        val docFreq = HashMap<String, Int>()
        docTokens.forEach { tokens ->
            tokens.toSet().forEach { token ->
                docFreq[token] = (docFreq[token] ?: 0) + 1
            }
        }

        val scored = ArrayList<Triple<Double, String, String>>()
        for (i in docs.indices) {
            val tokens = docTokens[i]
            if (tokens.isEmpty()) continue
            var score = 0.0
            queryTokens.forEach { token ->
                val hits = tokens.count { it == token || it.startsWith(token) }
                if (hits > 0) {
                    val df = (docFreq[token] ?: 1).toDouble()
                    score += hits.toDouble() * ln(1.0 + total / df)
                }
            }
            if (score > 0.0) {
                score /= ln(2.0 + tokens.size.toDouble())
                scored.add(Triple(score, docs[i].first, docs[i].second))
            }
        }

        if (scored.isEmpty()) {
            return ToolResult(true, "'" + query + "' ke baare me kuch save nahi mila")
        }
        val top = scored.sortedByDescending { it.first }.take(3)
        val lines = top.map { (_, kind, text) ->
            val label = if (kind == "note") "Note" else "Pehle"
            label + ": " + text.take(140)
        }
        return ToolResult(true, lines.joinToString(". "))
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase(Locale.getDefault())
            .replace(Regex("[^\\p{L}\\p{Nd} ]"), " ")
            .split(" ")
            .map { it.trim() }
            .filter { it.length > 2 && it !in STOP }

    private companion object {
        val STOP = setOf(
            "the", "and", "for", "kya", "hai", "tha", "the", "mera", "meri", "mere",
            "kar", "karo", "karna", "about", "with", "that", "this", "what", "was",
            "batao", "dhundo", "search", "find", "note", "notes"
        )
    }
}
