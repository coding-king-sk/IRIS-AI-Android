package com.irisx.ai.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** One selectable accent for the whole app. */
data class AccentOption(val id: String, val label: String, val color: Color)

/**
 * Design tokens ported 1:1 from the desktop IRIS renderer (Tailwind zinc/black
 * palette with the #00ff41 signature accent).
 *
 * The accent trio is Compose state, so picking a theme repaints the whole UI
 * instantly — no restart needed.
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

    /** IRIS signature accent: #00ff41 (live, theme-able) */
    var Accent by mutableStateOf(Color(0xFF00FF41))
        private set
    var AccentSoft by mutableStateOf(Color(0x2600FF41))
        private set
    var AccentBorder by mutableStateOf(Color(0x4D00FF41))
        private set

    val Cyan = Color(0xFF22D3EE)
    val Danger = Color(0xFFEF4444)

    /** border border-white/5 */
    val GlassBorder = Color(0x14FFFFFF)
    /** bg-zinc-950/40 */
    val GlassFill = Color(0x6609090B)
    val Scrim = Color(0x99000000)

    val accents: List<AccentOption> = listOf(
        AccentOption("green", "MATRIX GREEN", Color(0xFF00FF41)),
        AccentOption("cyan", "ICE CYAN", Color(0xFF22D3EE)),
        AccentOption("amber", "SOLAR AMBER", Color(0xFFF59E0B)),
        AccentOption("violet", "NEON VIOLET", Color(0xFFA855F7)),
        AccentOption("rose", "PLASMA ROSE", Color(0xFFF43F5E)),
        AccentOption("blue", "DEEP BLUE", Color(0xFF3B82F6)),
        AccentOption("white", "MONO WHITE", Color(0xFFE4E4E7))
    )

    fun option(id: String): AccentOption =
        accents.firstOrNull { it.id == id } ?: accents[0]

    /** Applies an accent everywhere. */
    fun apply(id: String) {
        val picked = option(id).color
        Accent = picked
        AccentSoft = picked.copy(alpha = 0.15f)
        AccentBorder = picked.copy(alpha = 0.30f)
    }
}
