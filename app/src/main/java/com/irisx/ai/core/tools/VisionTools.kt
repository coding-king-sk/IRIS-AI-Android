package com.irisx.ai.core.tools

import android.content.Context
import android.content.Intent
import com.irisx.ai.core.agent.IrisTool
import com.irisx.ai.core.agent.ToolResult
import com.irisx.ai.ui.vision.CameraVisionActivity

/** Opens the live camera vision screen: "ye kya hai?" */
class CameraVisionTool : IrisTool {
    override val name = "camera_vision"
    override val description =
        "Open the camera and describe what it sees, including any text in the frame."

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val intent = Intent(context, CameraVisionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            ToolResult(true, "Camera vision khol diya — cheez saamne rakho aur SCAN dabao.")
        }.getOrElse {
            ToolResult(false, "Camera vision khul nahi paya.")
        }
    }
}
