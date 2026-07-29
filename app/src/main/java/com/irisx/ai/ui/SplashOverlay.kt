package com.irisx.ai.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.irisx.ai.ui.theme.IrisColors
import com.irisx.ai.ui.theme.MonoTiny
import kotlinx.coroutines.delay

/** Cinematic boot animation: the AI core spins up, then hands over to the shell. */
@Composable
fun SplashOverlay(onDone: () -> Unit) {
    var started by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (leaving) 1.35f else if (started) 1f else 0.55f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "splashScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (leaving) 0f else if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = LinearEasing),
        label = "splashAlpha"
    )

    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    LaunchedEffect(Unit) {
        started = true
        delay(1500)
        leaving = true
        delay(650)
        onDone()
    }

    val accent = IrisColors.Accent
    val cyan = IrisColors.Cyan

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IrisColors.Black)
            .graphicsLayer { this.alpha = if (leaving) alpha else 1f },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(
                    modifier = Modifier
                        .size(220.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                            rotationZ = angle
                        }
                ) {
                    val r = size.minDimension / 2f
                    drawCircle(color = accent.copy(alpha = 0.10f), radius = r)
                    drawCircle(color = accent, radius = r * 0.72f, style = Stroke(width = 3f))
                    drawCircle(color = cyan, radius = r * 0.52f, style = Stroke(width = 2f))
                    drawCircle(color = accent.copy(alpha = 0.6f), radius = r * 0.34f, style = Stroke(width = 1.5f))
                    drawCircle(color = accent, radius = r * 0.10f)
                }
                Text(
                    text = "IRIS",
                    color = IrisColors.Zinc100,
                    fontWeight = FontWeight.Black,
                    fontSize = 26.sp,
                    letterSpacing = 6.sp,
                    modifier = Modifier.graphicsLayer { this.alpha = alpha }
                )
            }
            Text(
                text = "BOOTING VOICE LAYER",
                style = MonoTiny,
                color = IrisColors.Zinc500,
                modifier = Modifier
                    .padding(top = 22.dp)
                    .graphicsLayer { this.alpha = alpha }
            )
        }
    }
}
