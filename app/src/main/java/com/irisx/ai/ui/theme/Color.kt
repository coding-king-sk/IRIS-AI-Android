package com.irisx.ai.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Design tokens ported 1:1 from the desktop IRIS renderer (Tailwind zinc/black
 * palette with the #00ff41 signature accent).
 */
object IrisColors {
    val Black = Color(0xFF000000)
    val Zinc950 = Color(0xFF09090B)
    val Zinc900 = Color(0xFF18181B)
    val Zinc800 = Color(0xFF27272A)
    val Zinc600 = Color(0xFF52525B)
    val Zinc500 = Color(0xFF71717A)
    val Zinc400 = Color(0xFFA1A1AA)
    val Zinc200 = Color(0xFFE4E4E7)
    val Zinc100 = Color(0xFFF4F4F5)

    /** IRIS signature accent: #00ff41 */
    val Accent = Color(0xFF00FF41)
    val AccentSoft = Color(0x2600FF41)
    val AccentBorder = Color(0x4D00FF41)

    val Cyan = Color(0xFF22D3EE)
    val Danger = Color(0xFFEF4444)

    /** border border-white/5 */
    val GlassBorder = Color(0x14FFFFFF)
    /** bg-zinc-950/40 */
    val GlassFill = Color(0x6609090B)
    val Scrim = Color(0x99000000)
}
