package com.irisx.ai.core.agent

import android.content.Context

data class ToolCall(val name: String, val args: Map<String, String> = emptyMap())

data class ToolResult(val ok: Boolean, val message: String)

data class AgentReply(
    val text: String,
    val ok: Boolean = true,
    val toolName: String? = null,
    val usedCloud: Boolean = false
)

/** Every executable capability IRIS has on the device. */
interface IrisTool {
    val name: String
    val description: String

    /** param name -> human description (also used to build the cloud schema). */
    val params: Map<String, String>
        get() = emptyMap()

    fun run(context: Context, args: Map<String, String>): ToolResult
}
