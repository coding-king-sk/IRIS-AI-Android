package com.irisx.ai.core.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.irisx.ai.core.agent.IrisTool
import com.irisx.ai.core.agent.ToolResult
import com.irisx.ai.data.NotesStore

class NoteTool : IrisTool {
    override val name = "note"
    override val description = "Save a note locally on the device."
    override val params = mapOf("text" to "Note content")

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val text = args["text"].orEmpty().trim()
        if (text.isEmpty()) return ToolResult(false, "Note khali hai.")
        NotesStore(context).add(text)
        return ToolResult(true, "Note save kar liya.")
    }
}

class ReadNotesTool : IrisTool {
    override val name = "read_notes"
    override val description = "Read back the most recent saved notes."

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val notes = NotesStore(context).all().take(3)
        if (notes.isEmpty()) return ToolResult(true, "Abhi koi note save nahi hai.")
        val body = notes.mapIndexed { i, n -> (i + 1).toString() + ". " + n.text }
            .joinToString(" ")
        return ToolResult(true, "Aapke latest notes: $body")
    }
}

class WebSearchTool : IrisTool {
    override val name = "web_search"
    override val description = "Search the web in the browser (needs internet)."
    override val params = mapOf("query" to "Search query")

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val query = args["query"].orEmpty().trim()
        if (query.isEmpty()) return ToolResult(false, "Search query nahi mili.")
        val url = "https://www.google.com/search?q=" + Uri.encode(query)
        return if (context.startExternal(Intent(Intent.ACTION_VIEW, Uri.parse(url)))) {
            ToolResult(true, "'$query' web par search kar raha hoon.")
        } else {
            ToolResult(false, "Browser khul nahi paya.")
        }
    }
}
