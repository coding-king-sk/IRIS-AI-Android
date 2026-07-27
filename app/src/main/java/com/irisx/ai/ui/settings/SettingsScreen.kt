package com.irisx.ai.ui.settings

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irisx.ai.data.SettingsStore
import com.irisx.ai.service.OverlayBubbleService
import com.irisx.ai.ui.components.GlassPanel
import com.irisx.ai.ui.components.PanelHeader
import com.irisx.ai.ui.theme.IrisColors
import com.irisx.ai.ui.theme.MonoLabel
import com.irisx.ai.ui.theme.MonoTiny

@Composable
fun SettingsScreen(isSystemActive: Boolean) {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }

    var wakeWord by remember { mutableStateOf(store.wakeWord) }
    var apiKey by remember { mutableStateOf(store.apiKey) }
    var baseUrl by remember { mutableStateOf(store.baseUrl) }
    var model by remember { mutableStateOf(store.model) }
    var offlineFirst by remember { mutableStateOf(store.offlineFirst) }
    var localOnly by remember { mutableStateOf(store.localOnly) }
    var ttsEnabled by remember { mutableStateOf(store.ttsEnabled) }
    var hinglish by remember { mutableStateOf(store.hinglishMode) }
    var continuous by remember { mutableStateOf(store.continuousMode) }
    var haptics by remember { mutableStateOf(store.haptics) }
    var soundCues by remember { mutableStateOf(store.soundCues) }
    var bubbleOn by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 18) {
            PanelHeader(
                title = "CORE CONFIG",
                subtitle = if (isSystemActive) "SYSTEM ACTIVE" else "SYSTEM IDLE",
                trailing = {
                    Text(
                        if (localOnly) "LOCAL ONLY" else "HYBRID",
                        style = MonoTiny,
                        color = IrisColors.Accent
                    )
                }
            )

            FieldRow(label = "WAKE WORD", value = wakeWord) {
                wakeWord = it
                store.wakeWord = it
            }
            ToggleRow("OFFLINE STT FIRST", offlineFirst) {
                offlineFirst = it
                store.offlineFirst = it
            }
            ToggleRow("LOCAL ONLY (NO CLOUD)", localOnly) {
                localOnly = it
                store.localOnly = it
            }
            ToggleRow("VOICE REPLY (TTS)", ttsEnabled) {
                ttsEnabled = it
                store.ttsEnabled = it
            }
            ToggleRow("HINGLISH MODE", hinglish) {
                hinglish = it
                store.hinglishMode = it
            }
        }

        GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 18) {
            PanelHeader(
                title = "CONVERSATION",
                subtitle = "HANDS FREE FLOW",
                trailing = {
                    Text(
                        if (continuous) "CONTINUOUS" else "WAKE WORD ONLY",
                        style = MonoTiny,
                        color = IrisColors.Accent
                    )
                }
            )
            ToggleRow("CONTINUOUS MODE (AUTO FOLLOW UP)", continuous) {
                continuous = it
                store.continuousMode = it
            }
            ToggleRow("HAPTIC FEEDBACK", haptics) {
                haptics = it
                store.haptics = it
            }
            ToggleRow("SOUND CUES (BEEPS)", soundCues) {
                soundCues = it
                store.soundCues = it
            }
            Text(
                "Continuous mode on hone par jawab ke baad mic 3 turn tak khula rehta hai, wake word dobara bolna nahi padta.",
                style = MonoTiny,
                color = IrisColors.Zinc600,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 18) {
            PanelHeader(title = "FLOATING BUBBLE", subtitle = "IRIS OVER EVERY APP")
            ToggleRow("SHOW FLOATING BUBBLE", bubbleOn) { wanted ->
                if (wanted) {
                    if (OverlayBubbleService.canShow(context)) {
                        OverlayBubbleService.start(context)
                        bubbleOn = true
                    } else {
                        OverlayBubbleService.requestPermission(context)
                        bubbleOn = false
                    }
                } else {
                    OverlayBubbleService.stop(context)
                    bubbleOn = false
                }
            }
            ActionRow("DISPLAY OVER OTHER APPS") {
                OverlayBubbleService.requestPermission(context)
            }
            Text(
                "Bubble ko drag karke kahin bhi rakho, tap karne par IRIS khul jayega.",
                style = MonoTiny,
                color = IrisColors.Zinc600,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 18) {
            PanelHeader(title = "CLOUD BRAIN", subtitle = "OPTIONAL · USED ONLY WHEN ONLINE")
            FieldRow(label = "BASE URL", value = baseUrl) {
                baseUrl = it
                store.baseUrl = it
            }
            FieldRow(label = "MODEL", value = model) {
                model = it
                store.model = it
            }
            FieldRow(label = "API KEY", value = apiKey, secret = true) {
                apiKey = it
                store.apiKey = it
            }
            Text(
                "Key device par hi rehti hai. Khali chhod do to IRIS sirf local intents chalayega.",
                style = MonoTiny,
                color = IrisColors.Zinc600,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 18) {
            PanelHeader(title = "SYSTEM ACCESS", subtitle = "GRANT ONCE")
            ActionRow("ACCESSIBILITY (SCREEN CONTROL)") {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            ActionRow("NOTIFICATION ACCESS") {
                context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            }
            ActionRow("MODIFY SYSTEM SETTINGS (BRIGHTNESS)") {
                runCatching {
                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                    intent.data = android.net.Uri.parse("package:" + context.packageName)
                    context.startActivity(intent)
                }
            }
            ActionRow("EXACT ALARMS (REMINDERS)") {
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    } else {
                        context.startActivity(Intent(Settings.ACTION_DATE_SETTINGS))
                    }
                }
            }
            ActionRow("APP PERMISSIONS (MIC, CONTACTS, CALENDAR)") {
                runCatching {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = android.net.Uri.parse("package:" + context.packageName)
                    context.startActivity(intent)
                }
            }
            ActionRow("BATTERY OPTIMISATION") {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
            ActionRow("OFFLINE VOICE DATA (SPEECH)") {
                runCatching {
                    context.startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                }
            }
        }
    }
}

@Composable
private fun FieldRow(
    label: String,
    value: String,
    secret: Boolean = false,
    onChange: (String) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MonoTiny, color = IrisColors.Zinc600)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(IrisColors.Zinc950)
                .border(1.dp, IrisColors.GlassBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = IrisColors.Zinc100,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(IrisColors.Accent),
                visualTransformation = if (secret) {
                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                } else {
                    androidx.compose.ui.text.input.VisualTransformation.None
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MonoLabel, color = IrisColors.Zinc400)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = IrisColors.Black,
                checkedTrackColor = IrisColors.Accent,
                uncheckedTrackColor = IrisColors.Zinc900
            )
        )
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MonoLabel, color = IrisColors.Zinc400)
        Text("OPEN", style = MonoTiny, color = IrisColors.Accent)
    }
}
