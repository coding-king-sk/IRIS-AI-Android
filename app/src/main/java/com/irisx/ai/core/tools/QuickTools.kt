package com.irisx.ai.core.tools

import android.Manifest
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.irisx.ai.core.agent.IrisTool
import com.irisx.ai.core.agent.ToolResult
import com.irisx.ai.core.automation.Automator
import java.net.URLEncoder

/**
 * The small everyday switches — the ones that get used a hundred times a day.
 *
 * Android deliberately blocks apps from flipping some of these silently, so
 * every tool here either does the real thing or says plainly what the phone
 * will not allow and opens the right screen.
 */

/** Do Not Disturb on / off. */
class DndTool : IrisTool {
    override val name = "dnd"
    override val description = "Turn Do Not Disturb on or off."
    override val params = mapOf("state" to "on or off")

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val on = args["state"].orEmpty().lowercase() != "off"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return ToolResult(false, "Notification service nahi mila.")

        if (!nm.isNotificationPolicyAccessGranted) {
            context.startExternal(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return ToolResult(
                false,
                "DND ke liye ek baar permission chahiye \u2014 jo list khuli hai usme IRIS ko allow " +
                    "kar do, phir dobara bolo."
            )
        }
        return runCatching {
            nm.setInterruptionFilter(
                if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
            ToolResult(
                true,
                if (on) "Do not disturb on \u2014 ab koi tang nahi karega."
                else "DND off, notifications wapas chalu."
            )
        }.getOrElse { ToolResult(false, "DND change nahi ho paya.") }
    }
}

/** Silent / vibrate / normal ringer. */
class RingerTool : IrisTool {
    override val name = "ringer"
    override val description = "Switch the phone between silent, vibrate and normal."
    override val params = mapOf("mode" to "silent, vibrate or normal")

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val mode = args["mode"].orEmpty().lowercase()
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ToolResult(false, "Audio service nahi mila.")
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        val target = when {
            mode.startsWith("sil") -> AudioManager.RINGER_MODE_SILENT
            mode.startsWith("vib") -> AudioManager.RINGER_MODE_VIBRATE
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        if (target == AudioManager.RINGER_MODE_SILENT &&
            nm != null && !nm.isNotificationPolicyAccessGranted
        ) {
            context.startExternal(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return ToolResult(
                false,
                "Silent karne ke liye permission chahiye \u2014 khuli hui list me IRIS allow kar do."
            )
        }
        return runCatching {
            audio.ringerMode = target
            val label = when (target) {
                AudioManager.RINGER_MODE_SILENT -> "Silent kar diya."
                AudioManager.RINGER_MODE_VIBRATE -> "Vibrate pe daal diya."
                else -> "Awaaz wapas on kar di."
            }
            ToolResult(true, label)
        }.getOrElse { ToolResult(false, "Ringer mode change nahi hua.") }
    }
}

/** Bluetooth on / off, with an honest fallback on Android 13+. */
class BluetoothTool : IrisTool {
    override val name = "bluetooth"
    override val description = "Turn Bluetooth on or off."
    override val params = mapOf("state" to "on or off")

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val on = args["state"].orEmpty().lowercase() != "off"
        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: return ToolResult(false, "Is phone me bluetooth nahi mila.")

        val enabled = runCatching { adapter.isEnabled }.getOrDefault(false)
        if (enabled == on) {
            return ToolResult(true, if (on) "Bluetooth pehle se on hai." else "Bluetooth pehle se off hai.")
        }

        if (Build.VERSION.SDK_INT < 33) {
            @Suppress("DEPRECATION")
            val ok = runCatching { if (on) adapter.enable() else adapter.disable() }
                .getOrDefault(false)
            if (ok) {
                return ToolResult(true, if (on) "Bluetooth on kar diya." else "Bluetooth off kar diya.")
            }
        }
        if (on && context.startExternal(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))) {
            return ToolResult(true, "Bluetooth on karne ka dialog khol diya \u2014 Allow daba do.")
        }
        context.startExternal(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        return ToolResult(
            true,
            "Android 13+ pe koi app khud bluetooth toggle nahi kar sakti \u2014 settings khol di hai."
        )
    }
}

/** Hotspot: Android has no public toggle, so open the tether screen. */
class HotspotTool : IrisTool {
    override val name = "hotspot"
    override val description = "Open the hotspot / tethering screen."
    override val params = mapOf("state" to "on or off")

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val tether = Intent(Intent.ACTION_MAIN).setComponent(
            ComponentName("com.android.settings", "com.android.settings.TetherSettings")
        )
        if (context.startExternal(tether)) {
            return ToolResult(true, "Hotspot screen khol di \u2014 switch tap kar do.")
        }
        context.startExternal(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        return ToolResult(
            true,
            "Hotspot ko app se on nahi kar sakta (Android ka rule), network settings khol di hai."
        )
    }
}

/** "Papa ko location bhejo" — current location as a Maps link on WhatsApp. */
class LocationShareTool : IrisTool {
    override val name = "location_share"
    override val description = "Send your current location to a contact on WhatsApp."
    override val params = mapOf("contact" to "Contact name")

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val who = (args["contact"] ?: args["user"] ?: "").trim()

        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            context.startExternal(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + context.packageName)
                )
            )
            return ToolResult(
                false,
                "Location permission nahi hai \u2014 App info khol di, Location allow karke dobara bolo."
            )
        }

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return ToolResult(false, "Location service nahi mila.")
        var best: Location? = null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        for (provider in providers) {
            val loc = runCatching { lm.getLastKnownLocation(provider) }.getOrNull() ?: continue
            val current = best
            if (current == null || loc.time > current.time) best = loc
        }
        val location = best
            ?: return ToolResult(
                false,
                "Abhi location nahi mili. GPS on karke Maps ek second khol lo, phir bolo."
            )

        val link = "https://maps.google.com/?q=" + location.latitude + "," + location.longitude
        val text = "Meri abhi ki location: " + link
        val encoded = URLEncoder.encode(text, "UTF-8")

        val contact = runCatching {
            if (who.isBlank()) null else ContactResolver.resolve(context, who)
        }.getOrNull()
        val digits = contact?.number.orEmpty().replace(Regex("[^0-9]"), "")
        val number = when {
            digits.length == 10 -> "91" + digits
            digits.length > 10 -> digits
            else -> ""
        }

        val url = if (number.isBlank()) {
            "https://wa.me/?text=" + encoded
        } else {
            "https://wa.me/" + number + "?text=" + encoded
        }
        val opened = context.startExternal(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.whatsapp")
        ) || context.startExternal(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

        if (!opened) return ToolResult(false, "WhatsApp khul nahi paya.")

        val name = contact?.name ?: who
        if (!Automator.available()) {
            return ToolResult(
                true,
                "Location " + (if (name.isBlank()) "chat" else name) +
                    " ke chat me daal di \u2014 send daba do. (Accessibility on karoge to khud bhej dunga.)"
            )
        }
        Automator.waitForApp("com.whatsapp", 9000)
        Automator.sleep(1200)
        val sent = Automator.tapSend()
        return if (sent) {
            ToolResult(true, "Location " + (if (name.isBlank()) "chat" else name) + " ko bhej di.")
        } else {
            ToolResult(true, "Location type ho gayi \u2014 send button khud daba do.")
        }
    }
}
