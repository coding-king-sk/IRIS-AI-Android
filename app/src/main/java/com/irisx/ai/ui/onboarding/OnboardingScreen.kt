package com.irisx.ai.ui.onboarding

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.irisx.ai.core.voice.NeuralTts
import com.irisx.ai.data.SettingsStore
import com.irisx.ai.service.OverlayBubbleService
import com.irisx.ai.ui.components.GlassPanel
import com.irisx.ai.ui.components.PanelHeader
import com.irisx.ai.ui.theme.IrisColors
import com.irisx.ai.ui.theme.MonoLabel
import com.irisx.ai.ui.theme.MonoTiny
import kotlin.concurrent.thread

/**
 * First-run wizard. Android does not let an app grant these itself, so the job
 * here is simply to explain each one in plain Hinglish and open the right
 * screen with one tap.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val store = SettingsStore(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IrisColors.Black)
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 18) {
            PanelHeader(title = "IRIS SETUP", subtitle = "EK BAAR KA KAAM")
            Text(
                "Har cheez ek tap me. Jo abhi nahi dena, baad me Settings se de sakte ho.",
                style = MonoTiny,
                color = IrisColors.Zinc600,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Step(
            title = "1 \u00b7 ACCESSIBILITY (SCREEN CONTROL)",
            body = "WhatsApp/Instagram pe message khud bhejne, form bharne aur screen padhne ke liye. " +
                "Android 13/14 me pehle App info \u2192 3 dot \u2192 'Allow restricted settings' dabana padta hai."
        ) {
            runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }

        Step(
            title = "2 \u00b7 NOTIFICATION ACCESS",
            body = "Music control (Spotify / YT Music pause-next) aur notification padhne ke liye."
        ) {
            runCatching {
                context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            }
        }

        Step(
            title = "3 \u00b7 DO NOT DISTURB ACCESS",
            body = "'Silent mode' aur 'DND on karo' chalane ke liye zaroori hai."
        ) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
        }

        Step(
            title = "4 \u00b7 FLOATING BUBBLE",
            body = "Har app ke upar IRIS ka bubble + live subtitle ke liye 'Display over other apps'."
        ) {
            OverlayBubbleService.requestPermission(context)
        }

        Step(
            title = "5 \u00b7 BATTERY OPTIMISATION OFF",
            body = "Taaki background service (hamesha sunne wala mode) phone band na kare."
        ) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }

        Step(
            title = "6 \u00b7 EXACT ALARMS",
            body = "Reminder aur timer sahi time pe bajne ke liye."
        ) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                } else {
                    context.startActivity(Intent(Settings.ACTION_DATE_SETTINGS))
                }
            }
        }

        Step(
            title = "7 \u00b7 IRIS KI APNI AWAAZ (NEURAL VOICE)",
            body = "Ek baar ~30 MB model download \u2014 uske baad har phone pe wahi awaaz, bina internet.",
            action = "DOWNLOAD"
        ) {
            thread {
                if (NeuralTts.download(context)) {
                    store.nttsEnabled = true
                    NeuralTts.prepare(context)
                }
            }
        }

        Step(
            title = "8 \u00b7 NEURAL WAKE WORD",
            body = "Bina beep ke, offline wake word. Baad me bhi bol sakte ho: 'neural wake word setup karo'.",
            action = "LATER"
        ) { }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 24.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(IrisColors.Accent)
                .clickable {
                    store.onboardingDone = true
                    onDone()
                }
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                "HO GAYA \u00b7 IRIS CHALU KARO",
                style = MonoLabel,
                color = IrisColors.Black,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun Step(
    title: String,
    body: String,
    action: String = "OPEN",
    onClick: () -> Unit
) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 16) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MonoLabel, color = IrisColors.Zinc100)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, IrisColors.GlassBorder, RoundedCornerShape(10.dp))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(action, style = MonoTiny, color = IrisColors.Accent)
            }
        }
        Text(
            body,
            style = MonoTiny,
            color = IrisColors.Zinc600,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
