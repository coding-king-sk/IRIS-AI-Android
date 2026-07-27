package com.irisx.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irisx.ai.ui.components.StatusDot
import com.irisx.ai.ui.dashboard.DashboardScreen
import com.irisx.ai.ui.device.DeviceScreen
import com.irisx.ai.ui.gallery.GalleryScreen
import com.irisx.ai.ui.notes.NotesScreen
import com.irisx.ai.ui.settings.SettingsScreen
import com.irisx.ai.ui.theme.IrisColors
import com.irisx.ai.ui.theme.MonoTiny
import com.irisx.ai.ui.theme.TabLabel

private data class IrisTab(val id: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    IrisTab("DASHBOARD", "Command", Icons.Filled.GridView),
    IrisTab("NOTES", "Notes", Icons.Filled.StickyNote2),
    IrisTab("GALLERY", "Gallery", Icons.Filled.Image),
    IrisTab("DEVICE", "Device", Icons.Filled.PhoneAndroid),
    IrisTab("SETTINGS", "Settings", Icons.Filled.Settings)
)

@Composable
fun IrisRoot(
    state: IrisUiState,
    onToggleConnection: () -> Unit,
    onMicToggle: () -> Unit,
    onVisionMode: (VisionMode) -> Unit,
    onSendText: (String) -> Unit,
    onStopSpeaking: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf("DASHBOARD") }

    Column(modifier = modifier.background(IrisColors.Black)) {
        TopBar(state = state, onLogoClick = { activeTab = "DASHBOARD" })
        TabStrip(activeTab = activeTab, onSelect = { activeTab = it })

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(IrisColors.Zinc950, IrisColors.Black, IrisColors.Black)
                    )
                )
        ) {
            when (activeTab) {
                "DASHBOARD" -> DashboardScreen(
                    state = state,
                    onToggleConnection = onToggleConnection,
                    onMicToggle = onMicToggle,
                    onVisionMode = onVisionMode,
                    onSendText = onSendText,
                    onStopSpeaking = onStopSpeaking
                )
                "NOTES" -> NotesScreen()
                "GALLERY" -> GalleryScreen()
                "DEVICE" -> DeviceScreen()
                "SETTINGS" -> SettingsScreen(isSystemActive = state.isConnected)
            }
        }
    }
}

@Composable
private fun TopBar(state: IrisUiState, onLogoClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(IrisColors.Black)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onLogoClick)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(IrisColors.AccentSoft)
                    .border(1.dp, IrisColors.AccentBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "I",
                    color = IrisColors.Accent,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
            }
            Text(
                text = "IRIS AI",
                color = IrisColors.Zinc100,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(start = 10.dp)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.End) {
                Text("NETWORK", style = MonoTiny, color = IrisColors.Zinc400)
                Text(
                    text = if (state.networkOnline) "CONNECTED" else "OFFLINE",
                    style = MonoTiny,
                    color = if (state.networkOnline) IrisColors.Accent else IrisColors.Danger
                )
            }
            Box(modifier = Modifier.padding(start = 8.dp)) {
                StatusDot(
                    color = if (state.networkOnline) IrisColors.Accent else IrisColors.Danger,
                    size = 8
                )
            }
        }
    }
}

@Composable
private fun TabStrip(activeTab: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(IrisColors.Black)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tabs.forEach { tab ->
            val selected = tab.id == activeTab
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) IrisColors.AccentSoft else Color.Transparent)
                    .border(
                        1.dp,
                        if (selected) IrisColors.AccentBorder else Color.Transparent,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelect(tab.id) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    tint = if (selected) IrisColors.Accent else IrisColors.Zinc600,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = tab.label.uppercase(),
                    style = TabLabel,
                    color = if (selected) IrisColors.Accent else IrisColors.Zinc500
                )
            }
        }
    }
}
