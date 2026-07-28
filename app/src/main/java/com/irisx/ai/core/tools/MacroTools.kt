package com.irisx.ai.core.tools

import android.content.Context
import com.irisx.ai.core.agent.IrisTool
import com.irisx.ai.core.agent.LocalIntentParser
import com.irisx.ai.core.agent.ToolRegistry
import com.irisx.ai.core.agent.ToolResult
import com.irisx.ai.core.macros.MacroStore

/** Saves a custom voice shortcut, e.g. "office mode". */
class MacroSaveTool : IrisTool {
    override val name = "macro_save"
    override val description = "Save a custom voice shortcut that runs several commands in order."
    override val params = mapOf(
        "name" to "Shortcut name, e.g. office mode",
        "steps" to "Commands separated by comma or 'phir'"
    )

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val shortcut = args["name"].orEmpty().trim()
        val raw = args["steps"].orEmpty()
        if (shortcut.isBlank()) return ToolResult(false, "Shortcut ka naam batao.")

        val steps = raw.split(",", ";", " phir ", " then ", " aur ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (steps.isEmpty()) {
            return ToolResult(false, "Steps nahi mile. Aise bolo: shortcut banao office mode: wifi on, silent karo, alarm 9 baje")
        }

        MacroStore(context).save(shortcut, steps)
        return ToolResult(
            true,
            "'" + shortcut + "' save ho gaya — " + steps.size + " step. Ab bas '" + shortcut + "' bolna."
        )
    }
}

/** Runs a saved shortcut step by step through the local parser. */
class MacroRunTool : IrisTool {
    override val name = "macro_run"
    override val description = "Run a saved voice shortcut."
    override val params = mapOf("name" to "Shortcut name")

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val wanted = args["name"].orEmpty().trim()
        val store = MacroStore(context)
        val macro = store.find(wanted)
            ?: return ToolResult(
                false,
                "'" + wanted + "' naam ka shortcut nahi mila. Banane ke liye bolo: shortcut banao " +
                    wanted + ": step1, step2"
            )

        val registry = ToolRegistry(context)
        val done = mutableListOf<String>()
        val failed = mutableListOf<String>()

        for (step in macro.steps) {
            val call = LocalIntentParser.parse(step)
            if (call == null || call.name == "macro_run") {
                failed.add(step)
                continue
            }
            val result = registry.execute(call)
            if (result.ok) done.add(step) else failed.add(step)
        }

        val summary = StringBuilder(macro.name + " chalu: " + done.size + "/" + macro.steps.size + " step ho gaye.")
        if (failed.isNotEmpty()) {
            summary.append(" Ye nahi ho paye: ").append(failed.joinToString(", "))
        }
        return ToolResult(done.isNotEmpty(), summary.toString())
    }
}

/** Lists the saved shortcuts. */
class MacroListTool : IrisTool {
    override val name = "macro_list"
    override val description = "List the saved voice shortcuts."

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val macros = MacroStore(context).all()
        if (macros.isEmpty()) {
            return ToolResult(true, "Abhi koi shortcut nahi hai. Bolo: shortcut banao office mode: wifi on, silent karo")
        }
        val text = macros.joinToString(" | ") { it.name + " (" + it.steps.size + " step)" }
        return ToolResult(true, "Shortcuts: " + text)
    }
}
