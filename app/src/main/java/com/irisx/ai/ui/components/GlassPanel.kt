package com.irisx.ai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.irisx.ai.ui.theme.IrisColors
import com.irisx.ai.ui.theme.MonoLabel
import com.irisx.ai.ui.theme.MonoTiny

/**
 * Compose equivalent of the desktop `glassPanel` class:
 * bg-zinc-950/40 backdrop-blur-xl border border-white/5 rounded-2xl shadow-xl
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    radius: Int = 16,
    fill: Color = IrisColors.GlassFill,
    border: Color = IrisColors.GlassBorder,
    contentPadding: Int = 14,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(radius.dp))
            .background(fill)
            .border(BorderStroke(1.dp, border), RoundedCornerShape(radius.dp))
            .padding(contentPadding.dp),
        verticalArrangement = verticalArrangement,
        content = content
    )
}

@Composable
fun PanelHeader(
    title: String,
    subtitle: String? = null,
    accent: Color = IrisColors.Accent,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.padding(bottom = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(color = accent)
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(title, style = MonoLabel, color = IrisColors.Zinc200)
                if (subtitle != null) {
                    Text(subtitle, style = MonoTiny, color = IrisColors.Zinc600)
                }
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun StatusDot(color: Color, size: Int = 8) {
    Column(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(50))
            .background(color)
    ) {}
}
