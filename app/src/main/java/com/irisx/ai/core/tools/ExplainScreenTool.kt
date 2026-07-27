package com.irisx.ai.core.tools

import android.content.Context
import com.irisx.ai.core.agent.IrisTool
import com.irisx.ai.core.agent.ToolResult
import com.irisx.ai.service.IrisAccessibilityService

/**
 * "Isko samjhao" — reads whatever is on screen through the accessibility tree and
 * builds a short extractive summary on device (longest, most informative lines).
 */
class ExplainScreenTool : IrisTool {
    override val name = "explain_screen"
    override val description = "Read the current screen and explain it in a few short lines"

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val service = IrisAccessibilityService.instance
            ?: return ToolResult(false, "Accessibility service off hai, Settings me IRIS AI on karo")
        val raw = runCatching { service.readScreenText() }.getOrNull().orEmpty()
        if (raw.isBlank()) return ToolResult(false, "Screen se text nahi mila")

        val chunks = raw
            .split(Regex("[\\n.!?\u2022|]+"))
            .map { it.trim() }
            .filter { it.length > 12 }
            .distinct()
        if (chunks.isEmpty()) {
            return ToolResult(true, "Screen par sirf chhote labels hain: " + raw.take(160))
        }

        val ranked = chunks
            .sortedByDescending { chunk ->
                val words = chunk.split(" ").filter { it.length > 3 }.size
                words * 2 + chunk.length / 20
            }
            .take(3)

        val summary = ranked.joinToString(". ") { it.take(160) }
        return ToolResult(true, "Screen par ye hai: " + summary)
    }
}
