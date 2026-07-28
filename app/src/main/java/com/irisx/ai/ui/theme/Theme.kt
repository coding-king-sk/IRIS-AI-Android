package com.irisx.ai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** font-mono tracking-widest uppercase — used all over the desktop UI. */
val MonoLabel = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 10.sp,
    letterSpacing = 1.6.sp
)

val MonoTiny = MonoLabel.copy(fontSize = 9.sp)

val TabLabel = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = 11.sp,
    letterSpacing = 1.4.sp
)

@Composable
fun IrisTheme(content: @Composable () -> Unit) {
    // IRIS is dark-only by design; isSystemInDarkTheme is read to stay
    // future-proof if a light variant is added later.
    isSystemInDarkTheme()

    // Built inside the composable so accent changes repaint immediately.
    val scheme = darkColorScheme(
        primary = IrisColors.Accent,
        onPrimary = IrisColors.Black,
        secondary = IrisColors.Cyan,
        background = IrisColors.Black,
        onBackground = IrisColors.Zinc100,
        surface = IrisColors.Zinc950,
        onSurface = IrisColors.Zinc200,
        surfaceVariant = IrisColors.Zinc900,
        onSurfaceVariant = IrisColors.Zinc400,
        error = IrisColors.Danger,
        outline = IrisColors.GlassBorder
    )

    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(),
        content = content
    )
}
