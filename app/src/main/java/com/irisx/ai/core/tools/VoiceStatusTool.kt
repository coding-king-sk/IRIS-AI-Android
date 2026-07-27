package com.irisx.ai.core.tools

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.speech.SpeechRecognizer
import com.irisx.ai.core.agent.IrisTool
import com.irisx.ai.core.agent.ToolResult
import com.irisx.ai.service.IrisAccessibilityService
import com.irisx.ai.service.IrisNotificationListener

/**
 * Honest self-check: tells the user exactly which offline capability is live on
 * this specific phone, instead of pretending everything works.
 */
class VoiceStatusTool : IrisTool {
    override val name = "voice_status"
    override val description =
        "Report which offline capabilities and permissions are currently active"

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val parts = ArrayList<String>()

        val onDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }
                .getOrDefault(false)
        } else {
            false
        }
        parts.add(
            if (onDevice) "Offline speech engine ready hai"
            else "Offline speech engine nahi mila, Google app me offline language pack download karo"
        )

        parts.add(
            if (IrisAccessibilityService.instance != null) "Screen control on hai"
            else "Screen control off hai"
        )

        val notifications = runCatching { IrisNotificationListener.recent().isNotEmpty() }
            .getOrDefault(false)
        parts.add(
            if (notifications) "Notification access chal raha hai"
            else "Notification access se abhi kuch nahi aaya"
        )

        parts.add(
            if (Settings.canDrawOverlays(context)) "Floating bubble allowed hai"
            else "Floating bubble ki permission nahi hai"
        )

        parts.add(
            if (Settings.System.canWrite(context)) "Brightness control allowed hai"
            else "Brightness control ki permission nahi hai"
        )

        return ToolResult(true, parts.joinToString(". "))
    }
}
