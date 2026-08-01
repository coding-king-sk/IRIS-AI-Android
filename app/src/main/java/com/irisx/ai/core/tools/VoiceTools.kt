package com.irisx.ai.core.tools

import android.content.Context
import com.irisx.ai.core.agent.IrisTool
import com.irisx.ai.core.agent.ToolResult
import com.irisx.ai.core.voice.NeuralTts
import com.irisx.ai.data.SettingsStore
import kotlin.concurrent.thread

/**
 * "IRIS jaisi awaaz" - downloads and enables the on-device neural voice.
 * Everything runs locally afterwards; only the one-time model fetch needs data.
 */
class NeuralVoiceSetupTool : IrisTool {
    override val name = "neural_voice_setup"
    override val description = "Neural (AI) voice model download karke IRIS ki apni awaaz chalu karo"
    override val params: Map<String, String> = mapOf(
        "action" to "on | off | delete (khali chhodo to install kar dega)"
    )

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val store = SettingsStore(context)
        val action = (args["action"] ?: args["query"] ?: "").lowercase()
        if (action.contains("off") || action.contains("band") || action.contains("disable")) {
            store.nttsEnabled = false
            NeuralTts.stop()
            return ToolResult(true, "Neural voice band. Ab phone ki apni awaaz use hogi.")
        }
        if (action.contains("delete") || action.contains("hatao")) {
            store.nttsEnabled = false
            NeuralTts.delete(context)
            return ToolResult(true, "Neural voice model delete kar diya.")
        }
        if (NeuralTts.installed(context)) {
            store.nttsEnabled = true
            return ToolResult(true, "Neural voice pehle se ready hai - ab wahi bolegi.")
        }
        if (NeuralTts.downloading) {
            return ToolResult(true, "Model abhi download ho raha hai, thoda ruko.")
        }
        thread {
            val ok = NeuralTts.download(context)
            if (ok) {
                store.nttsEnabled = true
                NeuralTts.prepare(context)
            }
        }
        return ToolResult(
            true,
            "Neural voice model download shuru - takriban 30 MB, wifi pe 1-2 minute. " +
                "Ho jaane ke baad awaaz apne aap badal jayegi, aur phir bina internet ke chalegi."
        )
    }
}

class NeuralVoiceStatusTool : IrisTool {
    override val name = "neural_voice_status"
    override val description = "Neural voice model ka status batao"
    override val params: Map<String, String> = emptyMap()

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val store = SettingsStore(context)
        val installed = NeuralTts.installed(context)
        val text = buildString {
            append(if (installed) "Model installed (" + NeuralTts.sizeMb(context) + " MB)" else "Model nahi hai")
            append(" | ")
            append(if (store.nttsEnabled) "neural voice ON" else "system voice ON")
            if (NeuralTts.downloading) append(" | download chal raha hai")
            if (!installed) append(" | bolo: neural voice setup karo")
        }
        return ToolResult(true, text)
    }
}
