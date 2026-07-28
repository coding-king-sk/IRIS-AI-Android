package com.irisx.ai.ui.themes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.irisx.ai.data.SettingsStore
import com.irisx.ai.ui.components.AICoreSphere
import com.irisx.ai.ui.components.GlassPanel
import com.irisx.ai.ui.components.PanelHeader
import com.irisx.ai.ui.theme.IrisColors
import com.irisx.ai.ui.theme.MonoLabel
import com.irisx.ai.ui.theme.MonoTiny

/** Accent picker — changes repaint the whole app instantly. */
@Composable
fun ThemeScreen() {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    var selected by remember { mutableStateOf(store.accent) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 18) {
            PanelHeader(
                title = "LIVE PREVIEW",
                subtitle = IrisColors.option(selected).label,
                trailing = {
                    Text(selected.uppercase(), style = MonoTiny, color = IrisColors.Accent)
                }
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
                contentAlignment = Alignment.Center
            ) {
                AICoreSphere(
                    isConnected = true,
                    isSpeaking = false,
                    isListening = true,
                    amplitude = 0.55f,
                    diameter = 150
                )
            }
        }

        GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 18) {
            PanelHeader(title = "ACCENT", subtitle = "TAP TO APPLY · SAVED AUTOMATICALLY")
            IrisColors.accents.forEach { option ->
                val isActive = option.id == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isActive) IrisColors.AccentSoft else IrisColors.Zinc950)
                        .border(
                            1.dp,
                            if (isActive) IrisColors.AccentBorder else IrisColors.GlassBorder,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable {
                            selected = option.id
                            store.accent = option.id
                            IrisColors.apply(option.id)
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(option.color, option.color.copy(alpha = 0.35f))
                                    )
                                )
                                .border(1.dp, IrisColors.GlassBorder, CircleShape)
                        )
                        Text(
                            option.label,
                            style = MonoLabel,
                            color = if (isActive) IrisColors.Zinc100 else IrisColors.Zinc400,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                    Text(
                        if (isActive) "ACTIVE" else "APPLY",
                        style = MonoTiny,
                        color = if (isActive) IrisColors.Accent else IrisColors.Zinc600
                    )
                }
            }
            Text(
                "Accent turant har screen par lag jata hai — sphere, buttons, borders sab badal jate hain.",
                style = MonoTiny,
                color = IrisColors.Zinc600,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
