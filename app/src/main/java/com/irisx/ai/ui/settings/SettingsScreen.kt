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
import com.irisx.ai.core.voice.NeuralTts
import com.irisx.ai.core.voice.OpenWakeWord
import com.irisx.ai.data.SettingsStore
import com.irisx.ai.service.OverlayBubbleService
import com.irisx.ai.ui.components.GlassPanel
import com.irisx.ai.ui.components.PanelHeader
import com.irisx.ai.ui.theme.IrisColors
import com.irisx.ai.ui.theme.MonoLabel
import com.irisx.ai.ui.theme.MonoTiny
import kotlin.concurrent.thread

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
    var liveMode by remember { mutableStateOf(store.liveMode) }
    var haptics by remember { mutableStateOf(store.haptics) }
    var soundCues by remember { mutableStateOf(store.soundCues) }
    var subtitle by remember { mutableStateOf(store.bubbleSubtitle) }
    var neuralVoice by remember { mutableStateOf(store.nttsEnabled) }
    var neuralWake by remember { mutableStateOf(store.owwEnabled) }
    var wakeModel by remember { mutableStateOf(store.wakeModel) }
    var voskOn by remember { mutableStateOf(store.voskEnabled) }
    var voiceNote by remember { mutableStateOf("") }
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
                        if (liveMode) "LIVE CALL" else if (continuous) "CONTINUOUS" else "WAKE WORD ONLY",
                        style = MonoTiny,
                        color = IrisColors.Accent
                    )
                }
            )
            ToggleRow("LIVE MODE (CALL JAISI BAAT)", liveMode) {
                liveMode = it
                store.liveMode = it
            }
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
                "Live mode me har jawab ke baad mic khud khul jaata hai aur beech me wake word bolke IRIS ko tok sakte ho. " +
                    "Continuous mode sirf 3 follow-up turn deta hai.",
                style = MonoTiny,
                color = IrisColors.Zinc600,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 18) {
            PanelHeader(
                title = "VOICE MODELS",
                subtitle = "ON-DEVICE \u00b7 EK BAAR DOWNLOAD",
                trailing = {
                    Text(
                        if (neuralVoice && NeuralTts.installed(context)) "NEURAL" else "SYSTEM",
                        style = MonoTiny,
                        color = IrisColors.Accent
                    )
                }
            )
            ToggleRow("IRIS NEURAL VOICE (VITS)", neuralVoice) {
                neuralVoice = it
                store.nttsEnabled = it
                if (it && !NeuralTts.installed(context)) {
                    voiceNote = "Model download shuru\u2026 (~30 MB)"
                    thread {
                        val ok = NeuralTts.download(context)
                        if (ok) NeuralTts.prepare(context)
                    }
                }
            }
            ActionRow(
                if (NeuralTts.installed(context)) "NEURAL VOICE MODEL DELETE" else "NEURAL VOICE MODEL DOWNLOAD"
            ) {
                if (NeuralTts.installed(context)) {
                    NeuralTts.delete(context)
                    neuralVoice = false
                    store.nttsEnabled = false
                    voiceNote = "Model delete ho gaya."
                } else {
                    voiceNote = "Model download shuru\u2026 (~30 MB)"
                    thread {
                        if (NeuralTts.download(context)) {
                            store.nttsEnabled = true
                            NeuralTts.prepare(context)
                        }
                    }
                }
            }
            ToggleRow("NEURAL WAKE WORD (OPENWAKEWORD)", neuralWake) {
                neuralWake = it
                store.owwEnabled = it
                if (it) {
                    voiceNote = "Wake word model taiyaar ho raha hai\u2026"
                    thread { OpenWakeWord.download(context, store.wakeModel) }
                }
            }
            FieldRow(label = "WAKE MODEL", value = wakeModel) {
                wakeModel = it
                store.wakeModel = it
            }
            ToggleRow("VOSK OFFLINE STT", voskOn) {
                voskOn = it
                store.voskEnabled = it
            }
            if (voiceNote.isNotBlank()) {
                Text(voiceNote, style = MonoTiny, color = IrisColors.Accent, modifier = Modifier.padding(top = 6.dp))
            }
            Text(
                "Neural voice = IRIS ki apni awaaz, phone ke TTS se alag, aur offline. " +
                    "Wake model options: hey_jarvis_v0.1, alexa_v0.1, hey_mycroft_v0.1, hey_rhasspy_v0.1.",
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
            ToggleRow("LIVE SUBTITLE IN BUBBLE", subtitle) {
                subtitle = it
                store.bubbleSubtitle = it
            }
            ActionRow("DISPLAY OVER OTHER APPS") {
                OverlayBubbleService.requestPermission(context)
            }
            Text(
                "Bubble ko drag karke kahin bhi rakho. Live subtitle on ho to jo bol rahe ho aur IRIS ka jawab dono bubble me dikhte hain.",
                style = MonoTiny,
                color = IrisColors.Zinc600,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 18) {
            PanelHeader(title = "CLOUD BRAIN", subtitle = "OPTIONAL \u00b7 USED ONLY WHEN ONLINE")
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
            ActionRow("NOTIFICATION ACCESS (MUSIC CONTROL)") {
                context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            }
            ActionRow("DO NOT DISTURB ACCESS") {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
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
            ActionRow("SETUP WIZARD DOBARA DIKHAO") {
                store.onboardingDone = false
                voiceNote = "App dobara kholo, wizard aa jayega."
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
