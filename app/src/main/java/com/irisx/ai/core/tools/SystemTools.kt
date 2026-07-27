package com.irisx.ai.core.tools

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.irisx.ai.core.agent.IrisTool
import com.irisx.ai.core.agent.ToolResult
import com.irisx.ai.service.IrisAccessibilityService

/** Screen brightness control using WRITE_SETTINGS. */
class BrightnessTool : IrisTool {
    override val name = "brightness"
    override val description = "Set screen brightness level"
    override val params = mapOf("percent" to "0-100, or up, down, max, min")

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        if (!Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:" + context.packageName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
            return ToolResult(
                false,
                "Brightness badalne ke liye 'Modify system settings' allow karo, screen khol di hai"
            )
        }

        val current = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrDefault(128)

        val raw = (args["percent"] ?: args["level"] ?: "").trim().lowercase()
        val digits = raw.filter { it.isDigit() }
        val target = when {
            digits.isNotEmpty() -> (digits.toInt().coerceIn(1, 100)) * 255 / 100
            raw == "up" -> (current + 51).coerceAtMost(255)
            raw == "down" -> (current - 51).coerceAtLeast(5)
            raw == "max" || raw == "full" -> 255
            raw == "min" -> 5
            else -> return ToolResult(false, "Brightness kitni karni hai?")
        }

        return runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                target
            )
            ToolResult(true, "Brightness " + (target * 100 / 255).toString() + " percent kar di")
        }.getOrElse { ToolResult(false, "Brightness set nahi ho payi") }
    }
}

/**
 * Opens the system toggle for wifi, data, bluetooth, airplane mode or hotspot.
 * Android 10+ blocks apps from flipping these switches directly, so IRIS opens
 * the exact panel instead of pretending it worked.
 */
class ConnectivityTool : IrisTool {
    override val name = "connectivity"
    override val description =
        "Open the system toggle for wifi, mobile data, bluetooth, airplane mode or hotspot"
    override val params = mapOf("target" to "wifi, data, bluetooth, airplane or hotspot")

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val target = (args["target"] ?: "").lowercase().trim()
        val action = when {
            target.contains("hotspot") || target.contains("tether") ->
                Settings.ACTION_WIRELESS_SETTINGS
            target.contains("airplane") || target.contains("flight") ->
                Settings.ACTION_AIRPLANE_MODE_SETTINGS
            target.contains("blue") -> Settings.ACTION_BLUETOOTH_SETTINGS
            target.contains("data") || target.contains("internet") ->
                Settings.Panel.ACTION_INTERNET_CONNECTIVITY
            target.contains("wifi") || target.contains("wi-fi") -> Settings.Panel.ACTION_WIFI
            else -> return ToolResult(false, "Ye toggle samajh nahi aaya")
        }
        val intent = Intent(action)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            ToolResult(true, target + " ka panel khol diya, switch tap kar do")
        }.getOrElse { ToolResult(false, "Ye settings screen nahi khul payi") }
    }
}

/** Takes a screenshot through the accessibility service (Android 11+). */
class ScreenshotTool : IrisTool {
    override val name = "screenshot"
    override val description = "Take a screenshot of the current screen"

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return ToolResult(false, "Screenshot ke liye Android 11 ya usse naya chahiye")
        }
        val service = IrisAccessibilityService.instance
            ?: return ToolResult(false, "Accessibility service off hai, Settings me IRIS AI on karo")
        val ok = runCatching {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
        }.getOrDefault(false)
        return if (ok) ToolResult(true, "Screenshot le liya")
        else ToolResult(false, "Screenshot fail hua")
    }
}
