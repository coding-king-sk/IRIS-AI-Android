package com.irisx.ai.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irisx.ai.ui.ChatLine
import com.irisx.ai.ui.IrisUiState
import com.irisx.ai.ui.Role
import com.irisx.ai.ui.VisionMode
import com.irisx.ai.ui.components.AICoreSphere
import com.irisx.ai.ui.components.GlassPanel
import com.irisx.ai.ui.components.PanelHeader
import com.irisx.ai.ui.theme.IrisColors
import com.irisx.ai.ui.theme.MonoLabel
import com.irisx.ai.ui.theme.MonoTiny

/**
 * Phone-shaped rebuild of the desktop 12-column dashboard:
 * telemetry rail (top) -> AI core -> control dock -> live transcript.
 */
@Composable
fun DashboardScreen(
    state: IrisUiState,
    onToggleConnection: () -> Unit,
    onMicToggle: () -> Unit,
    onVisionMode: (VisionMode) -> Unit,
    onSendText: (String) -> Unit,
    onStopSpeaking: () -> Unit
) {
    var showVisionMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TelemetryRail(state = state)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            contentAlignment = Alignment.Center
        ) {
            AICoreSphere(
                isConnected = state.isConnected,
                isSpeaking = state.isSpeaking,
                isListening = state.isListening,
                amplitude = state.amplitude,
                diameter = 210
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 150.dp)
            ) {
                Text(
                    text = state.status.uppercase(),
                    style = MonoLabel,
                    color = if (state.isConnected) IrisColors.Accent else IrisColors.Zinc500
                )
                Text(
                    text = "ENGINE ${'$'}{state.engineMode}" + (state.lastTool?.let { " \u00b7 ${'$'}it" } ?: ""),
                    style = MonoTiny,
                    color = IrisColors.Zinc600
                )
            }
        }

        if (showVisionMenu && state.isConnected) {
            VisionMenu(
                current = state.visionMode,
                onPick = {
                    onVisionMode(it)
                    showVisionMenu = false
                }
            )
        }

        ControlDock(
            state = state,
            onVisionClick = { if (state.isConnected) showVisionMenu = !showVisionMenu },
            onToggleConnection = onToggleConnection,
            onMicToggle = onMicToggle
        )

        TranscriptPanel(
            lines = state.transcript,
            isSpeaking = state.isSpeaking,
            onStopSpeaking = onStopSpeaking,
            modifier = Modifier.weight(1f)
        )

        CommandInput(onSend = onSendText)
    }
}

@Composable
private fun TelemetryRail(state: IrisUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        TelemetryCard(
            label = "CORE",
            value = if (state.isConnected) "ACTIVE" else "IDLE",
            accent = if (state.isConnected) IrisColors.Accent else IrisColors.Zinc600,
            modifier = Modifier.weight(1f)
        )
        TelemetryCard(
            label = "MIC",
            value = when {
                state.isMuted -> "MUTED"
                state.isListening -> "LIVE"
                else -> "WAKE"
            },
            accent = when {
                state.isMuted -> IrisColors.Danger
                state.isListening -> IrisColors.Cyan
                else -> IrisColors.Accent
            },
            modifier = Modifier.weight(1f)
        )
        TelemetryCard(
            label = "OPTICS",
            value = state.visionMode.name,
            accent = when (state.visionMode) {
                VisionMode.CAMERA -> IrisColors.Accent
                VisionMode.SCREEN -> IrisColors.Cyan
                VisionMode.OFF -> IrisColors.Zinc600
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TelemetryCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    GlassPanel(modifier = modifier, radius = 14, contentPadding = 10) {
        Text(label, style = MonoTiny, color = IrisColors.Zinc600)
        Text(value, style = MonoLabel, color = accent)
    }
}

@Composable
private fun VisionMenu(current: VisionMode, onPick: (VisionMode) -> Unit) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        radius = 18,
        fill = Color(0xF209090B),
        contentPadding = 8
    ) {
        Text(
            "OPTICS FEED",
            style = MonoTiny,
            color = IrisColors.Zinc500,
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
        )
        VisionRow("LENS", Icons.Filled.CameraAlt, current == VisionMode.CAMERA, IrisColors.Accent) {
            onPick(VisionMode.CAMERA)
        }
        VisionRow("DISPLAY", Icons.Filled.Monitor, current == VisionMode.SCREEN, IrisColors.Cyan) {
            onPick(VisionMode.SCREEN)
        }
        VisionRow("OFFLINE", Icons.Filled.Close, current == VisionMode.OFF, IrisColors.Zinc500) {
            onPick(VisionMode.OFF)
        }
    }
}

@Composable
private fun VisionRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) accent.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = label, tint = if (active) accent else IrisColors.Zinc400, modifier = Modifier.size(14.dp))
        Text(label, style = MonoLabel, color = if (active) accent else IrisColors.Zinc400)
    }
}

@Composable
private fun ControlDock(
    state: IrisUiState,
    onVisionClick: () -> Unit,
    onToggleConnection: () -> Unit,
    onMicToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0x99000000))
            .border(1.dp, IrisColors.GlassBorder, RoundedCornerShape(28.dp))
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        val visionTint = when {
            !state.isConnected -> IrisColors.Zinc600
            state.visionMode == VisionMode.CAMERA -> IrisColors.Accent
            state.visionMode == VisionMode.SCREEN -> IrisColors.Cyan
            else -> IrisColors.Zinc400
        }
        CircleButton(
            icon = if (state.visionMode == VisionMode.SCREEN) Icons.Filled.Monitor else Icons.Filled.CameraAlt,
            tint = visionTint,
            enabled = state.isConnected,
            onClick = onVisionClick
        )

        Spacer(modifier = Modifier.size(14.dp))

        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(50))
                .background(if (state.isConnected) IrisColors.Danger else IrisColors.Accent)
                .clickable(onClick = onToggleConnection),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (state.isConnected) Icons.Filled.CallEnd else Icons.Filled.Call,
                contentDescription = "toggle core",
                tint = if (state.isConnected) Color.White else IrisColors.Black,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.size(14.dp))

        CircleButton(
            icon = if (state.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
            tint = when {
                !state.isConnected -> IrisColors.Zinc600
                state.isMuted -> IrisColors.Danger
                else -> IrisColors.Accent
            },
            enabled = state.isConnected,
            onClick = onMicToggle
        )
    }
}

@Composable
private fun CircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(50))
            .background(IrisColors.Zinc900.copy(alpha = if (enabled) 0.8f else 0.4f))
            .border(1.dp, tint.copy(alpha = 0.25f), RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun TranscriptPanel(
    lines: List<ChatLine>,
    isSpeaking: Boolean,
    onStopSpeaking: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    GlassPanel(modifier = modifier.fillMaxWidth(), radius = 18) {
        PanelHeader(
            title = "LIVE FEED",
            subtitle = "COMMAND STREAM",
            trailing = {
                if (isSpeaking) {
                    Text(
                        "STOP",
                        style = MonoTiny,
                        color = IrisColors.Danger,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onStopSpeaking)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        )

        if (lines.isEmpty()) {
            Text(
                "Say \"Hey IRIS\" or type a command below.",
                style = MonoTiny,
                color = IrisColors.Zinc600
            )
        }

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(lines) { line ->
                val accent = when (line.role) {
                    Role.USER -> IrisColors.Cyan
                    Role.IRIS -> IrisColors.Accent
                    Role.SYSTEM -> IrisColors.Zinc600
                }
                Column {
                    Text(
                        text = when (line.role) {
                            Role.USER -> "YOU"
                            Role.IRIS -> "IRIS"
                            Role.SYSTEM -> "SYS"
                        },
                        style = MonoTiny,
                        color = accent
                    )
                    Text(
                        text = line.text,
                        color = IrisColors.Zinc200,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandInput(onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(IrisColors.GlassFill)
            .border(1.dp, IrisColors.GlassBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = TextStyle(
                color = IrisColors.Zinc100,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            ),
            cursorBrush = SolidColor(IrisColors.Accent),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (text.isEmpty()) {
                    Text("type a command…", style = MonoTiny, color = IrisColors.Zinc600)
                }
                inner()
            }
        )
        Icon(
            imageVector = Icons.Filled.Send,
            contentDescription = "send",
            tint = if (text.isBlank()) IrisColors.Zinc600 else IrisColors.Accent,
            modifier = Modifier
                .size(20.dp)
                .clickable(enabled = text.isNotBlank()) {
                    onSend(text)
                    text = ""
                }
        )
    }
}
