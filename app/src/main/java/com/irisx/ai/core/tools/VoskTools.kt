package com.irisx.ai.core.tools

import android.content.Context
import com.irisx.ai.core.agent.IrisTool
import com.irisx.ai.core.agent.ToolResult
import com.irisx.ai.core.voice.VoskEngine
import com.irisx.ai.data.SettingsStore

/** Downloads the offline Vosk model and switches IRIS over to it. */
class VoskSetupTool : IrisTool {
    override val name = "offline_voice_setup"
    override val description =
        "Download the offline speech model so voice recognition works without internet."

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val engine = VoskEngine(context)
        val settings = SettingsStore(context)

        if (engine.isReady) {
            settings.voskEnabled = true
            return ToolResult(true, "Offline voice model pehle se ready hai — ab wahi use hoga.")
        }

        val ok = engine.downloadModel()
        return if (ok) {
            settings.voskEnabled = true
            ToolResult(true, "Offline voice model install ho gaya. Ab bina internet ke bhi sun paunga.")
        } else {
            ToolResult(false, "Model download nahi ho paya. Internet check karke dobara bolo.")
        }
    }
}

/** Tells whether offline recognition is active. */
class VoskStatusTool : IrisTool {
    override val name = "offline_voice_status"
    override val description = "Report whether the offline speech model is installed and active."

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val engine = VoskEngine(context)
        val settings = SettingsStore(context)
        val text = when {
            engine.isReady && settings.voskEnabled -> "Offline voice engine chalu hai (Vosk)."
            engine.isReady -> "Model install hai par offline engine band hai. Settings se on karo."
            else -> "Offline model install nahi hai. Bolo: offline voice setup karo."
        }
        return ToolResult(true, text)
    }
}
