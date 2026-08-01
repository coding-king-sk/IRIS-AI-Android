package com.irisx.ai.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.irisx.ai.ui.theme.IrisColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Native replacement for the desktop three.js `AICoreSphere`.
 *
 * The ring of bars around the core is a real waveform: every new mic amplitude
 * value is pushed into a rolling history, so the orb physically moves with the
 * user's voice instead of just pulsing on a timer.
 */
@Composable
fun AICoreSphere(
    isConnected: Boolean,
    isSpeaking: Boolean,
    isListening: Boolean,
    amplitude: Float,
    modifier: Modifier = Modifier,
    diameter: Int = 240
) {
    val transition = rememberInfiniteTransition(label = "core")

    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isSpeaking) 6000 else 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )

    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isSpeaking) 700 else 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Rolling waveform history — one slot per bar around the orb.
    val wave = remember {
        mutableStateListOf<Float>().also { list -> repeat(BARS) { list.add(0f) } }
    }
    LaunchedEffect(amplitude, isSpeaking, isListening) {
        val level = when {
            !isConnected -> 0f
            isListening -> amplitude.coerceIn(0f, 1f)
            // While IRIS talks there is no mic level, so animate a gentle voice.
            isSpeaking -> 0.25f + amplitude.coerceIn(0f, 1f) * 0.5f
            else -> amplitude.coerceIn(0f, 1f) * 0.3f
        }
        if (wave.isNotEmpty()) wave.removeAt(0)
        wave.add(level)
    }

    val accent: Color = when {
        !isConnected -> IrisColors.Zinc600
        isSpeaking -> IrisColors.Accent
        isListening -> IrisColors.Cyan
        else -> IrisColors.Accent.copy(alpha = 0.75f)
    }

    Box(modifier = modifier.size(diameter.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(diameter.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val base = size.minDimension / 2f
            val energy = if (isConnected) (0.08f + amplitude * 0.35f) else 0f
            val r = base * 0.55f * pulse * (1f + energy)

            // Outer bloom — mimics blur-[180px] radial glow of the web build.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = base
                ),
                radius = base,
                center = Offset(cx, cy)
            )

            // Core body
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.55f),
                        accent.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = r
                ),
                radius = r,
                center = Offset(cx, cy)
            )

            drawCircle(
                color = accent.copy(alpha = 0.85f),
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = 1.6f)
            )

            // Wireframe latitude rings (three.js wireframe sphere analogue)
            for (i in 1..5) {
                val squash = sin(i * PI / 6.0).toFloat()
                drawOval(
                    color = accent.copy(alpha = 0.16f + 0.05f * i),
                    topLeft = Offset(cx - r, cy - r * squash),
                    size = androidx.compose.ui.geometry.Size(r * 2, r * 2 * squash),
                    style = Stroke(width = 1f)
                )
            }

            // Real-time voice waveform ring
            val ringRadius = r * 1.16f
            for (i in 0 until BARS) {
                val level = wave.getOrElse(i) { 0f }
                val idle = 0.05f + 0.03f * sin((i * 0.7f + spin / 20f).toDouble()).toFloat()
                val height = r * (0.10f + max(level, idle) * 0.75f)
                val angle = ((i.toFloat() / BARS) * 360f - 90f) * (PI / 180.0)
                val ca = cos(angle).toFloat()
                val sa = sin(angle).toFloat()
                val startX = cx + ca * ringRadius
                val startY = cy + sa * ringRadius
                val endX = cx + ca * (ringRadius + height)
                val endY = cy + sa * (ringRadius + height)
                drawLine(
                    color = accent.copy(alpha = 0.35f + max(level, idle) * 0.6f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }

            // Orbiting particles
            val particles = 46
            for (i in 0 until particles) {
                val angle = ((i.toFloat() / particles) * 360f + spin) * (PI / 180.0)
                val wobble = 1f + 0.14f * sin((i + spin / 12f).toDouble()).toFloat()
                val pr = r * 1.42f * wobble
                val px = cx + cos(angle).toFloat() * pr
                val py = cy + sin(angle).toFloat() * pr * 0.72f
                drawCircle(
                    color = accent.copy(alpha = if (i % 4 == 0) 0.9f else 0.35f),
                    radius = if (i % 4 == 0) 2.4f else 1.4f,
                    center = Offset(px, py)
                )
            }

            // Live amplitude halo while listening / speaking
            if (isConnected && (isSpeaking || isListening)) {
                drawCircle(
                    color = accent.copy(alpha = 0.30f),
                    radius = r * (1.75f + amplitude * 0.5f),
                    center = Offset(cx, cy),
                    style = Stroke(width = 2f)
                )
            }
        }
    }
}

private const val BARS = 64
