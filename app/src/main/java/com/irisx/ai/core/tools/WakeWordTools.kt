package com.irisx.ai.core.tools

import android.content.Context
import com.irisx.ai.core.agent.IrisTool
import com.irisx.ai.core.agent.ToolResult
import com.irisx.ai.core.voice.OpenWakeWord
import com.irisx.ai.data.SettingsStore

/** Installs the openWakeWord neural models and switches the wake loop over. */
class NeuralWakeSetupTool : IrisTool {
    override val name = "neural_wake_setup"
    override val description =
        "Download the openWakeWord neural models so the wake word works online and offline."

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val settings = SettingsStore(context)
        val requested = (args["model"] ?: args["wake"] ?: args["query"] ?: "")
            .lowercase()
            .trim()
        if (requested.isNotBlank()) {
            OpenWakeWord.MODELS.entries
                .firstOrNull { requested.contains(it.key) || requested.contains(it.value) }
                ?.let { settings.wakeModel = it.value }
        }

        val engine = OpenWakeWord(context)
        if (engine.isReady) {
            settings.owwEnabled = true
            return ToolResult(
                true,
                "Neural wake word pehle se ready hai. Bolo: \"" + engine.spokenPhrase + "\"."
            )
        }

        val ok = engine.downloadModels()
        return if (ok) {
            settings.owwEnabled = true
            ToolResult(
                true,
                "Neural wake word install ho gaya (~5 MB). Ab bolo: \"" + engine.spokenPhrase +
                    "\". Ye internet ke saath aur bina, dono me chalta hai \u2014 na beep, na mic blink."
            )
        } else {
            ToolResult(false, "Wake word model download nahi hua. Internet check karke dobara bolo.")
        }
    }
}

/** Reports which wake-word engine is live right now. */
class NeuralWakeStatusTool : IrisTool {
    override val name = "neural_wake_status"
    override val description = "Report which wake word engine is active and what phrase to say."

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val settings = SettingsStore(context)
        val engine = OpenWakeWord(context)
        val text = when {
            engine.isReady && settings.owwEnabled ->
                "Neural wake word (openWakeWord) chalu hai. Bolo: \"" + engine.spokenPhrase +
                    "\". Internet ho ya na ho, dono me chalega."
            engine.isReady ->
                "Neural model install hai par band hai. Bolo: neural wake word chalu karo."
            settings.voskEnabled ->
                "Abhi Vosk offline listener chal raha hai. Behtar ke liye bolo: neural wake word setup karo."
            else ->
                "Abhi phone ka system recognizer chal raha hai (beep wala). Bolo: " +
                    "neural wake word setup karo."
        }
        return ToolResult(true, text)
    }
}
