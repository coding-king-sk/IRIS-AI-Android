package com.irisx.ai.core.tools

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.Settings
import android.view.KeyEvent
import com.irisx.ai.core.agent.IrisTool
import com.irisx.ai.core.agent.ToolResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmTool : IrisTool {
    override val name = "alarm"
    override val description = "Set a device alarm at a given hour and minute (24h)."
    override val params = mapOf("hour" to "Hour 0-23", "minute" to "Minute 0-59")

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val hour = args["hour"]?.toIntOrNull() ?: 7
        val minute = args["minute"]?.toIntOrNull() ?: 0
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "IRIS alarm")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        val label = String.format(Locale.US, "%02d:%02d", hour, minute)
        return if (context.startExternal(intent)) {
            ToolResult(true, "Alarm $label par set kar diya.")
        } else {
            ToolResult(false, "Alarm set nahi ho paya.")
        }
    }
}

class TimerTool : IrisTool {
    override val name = "timer"
    override val description = "Start a countdown timer."
    override val params = mapOf(
        "value" to "Numeric amount",
        "unit" to "second(s), minute(s) or hour(s)"
    )

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val value = args["value"]?.toIntOrNull() ?: 5
        val unit = args["unit"].orEmpty().lowercase()
        val seconds = when {
            unit.startsWith("sec") -> value
            unit.startsWith("hour") || unit.startsWith("ghant") -> value * 3600
            else -> value * 60
        }
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "IRIS timer")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return if (context.startExternal(intent)) {
            ToolResult(true, "$value $unit ka timer chalu.")
        } else {
            ToolResult(false, "Timer start nahi hua.")
        }
    }
}

class FlashlightTool : IrisTool {
    override val name = "flashlight"
    override val description = "Turn the phone torch on or off."
    override val params = mapOf("state" to "on or off")

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val on = args["state"].orEmpty().lowercase() != "off"
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return ToolResult(false, "Camera service nahi mila.")
        return runCatching {
            val id = manager.cameraIdList.firstOrNull()
                ?: return ToolResult(false, "Torch available nahi hai.")
            manager.setTorchMode(id, on)
            ToolResult(true, if (on) "Torch on kar di." else "Torch off kar di.")
        }.getOrElse { ToolResult(false, "Torch control fail hua.") }
    }
}

class VolumeTool : IrisTool {
    override val name = "volume"
    override val description = "Change media volume."
    override val params = mapOf("direction" to "up, down or mute")

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ToolResult(false, "Audio service nahi mila.")
        return when (args["direction"].orEmpty().lowercase()) {
            "down" -> {
                audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI
                )
                ToolResult(true, "Volume kam kar di.")
            }
            "mute" -> {
                audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_MUTE,
                    AudioManager.FLAG_SHOW_UI
                )
                ToolResult(true, "Mute kar diya.")
            }
            else -> {
                audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_RAISE,
                    AudioManager.FLAG_SHOW_UI
                )
                ToolResult(true, "Volume badha di.")
            }
        }
    }
}

class MediaTool : IrisTool {
    override val name = "media"
    override val description = "Control media playback."
    override val params = mapOf("action" to "play, pause, next or previous")

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ToolResult(false, "Audio service nahi mila.")
        val action = args["action"].orEmpty().lowercase()
        val keyCode = when (action) {
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> KeyEvent.KEYCODE_MEDIA_PLAY
        }
        return runCatching {
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            ToolResult(true, "Media: $action.")
        }.getOrElse { ToolResult(false, "Media control fail hua.") }
    }
}

class BatteryTool : IrisTool {
    override val name = "battery"
    override val description = "Report battery percentage and charging state."

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return ToolResult(false, "Battery info nahi mili.")
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        val suffix = if (charging) " aur charging chal rahi hai." else "."
        return ToolResult(true, "Battery $level percent hai$suffix")
    }
}

class ClockTool : IrisTool {
    override val name = "clock"
    override val description = "Tell the current time or date."
    override val params = mapOf("kind" to "time or date")

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val now = Date()
        return if (args["kind"].orEmpty() == "date") {
            val fmt = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
            ToolResult(true, "Aaj " + fmt.format(now) + " hai.")
        } else {
            val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
            ToolResult(true, "Abhi " + fmt.format(now) + " baje hain.")
        }
    }
}

class SettingsPanelTool : IrisTool {
    override val name = "settings_panel"
    override val description = "Open a system settings screen."
    override val params = mapOf("panel" to "wifi, bluetooth, sound, display or main")

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val action = when (args["panel"].orEmpty().lowercase()) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return if (context.startExternal(Intent(action))) {
            ToolResult(true, "Settings khol di.")
        } else {
            ToolResult(false, "Settings khul nahi payi.")
        }
    }
}
