package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.ui.theme.LocalAppearancePreferences
import com.r0ybt.arachn0de.ui.theme.LocalAppearancePalette
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.r0ybt.arachn0de.pomodoro.PomodoroAppearance
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

@Composable
private fun pomodoroHaloIntensity(active: Boolean): androidx.compose.runtime.State<Float> {
    val intensity = remember { Animatable(.35f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(active, lifecycle) {
        if (active) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val motionScale = coroutineContext[MotionDurationScale]
            snapshotFlow { motionScale?.scaleFactor ?: 1f }.collectLatest { scale ->
                if (scale <= 0f) {
                    intensity.snapTo(1f)
                } else {
                    while (isActive) {
                        intensity.animateTo(1f, tween(4000, easing = FastOutSlowInEasing))
                        intensity.animateTo(.35f, tween(4000, easing = FastOutSlowInEasing))
                    }
                }
            }
        }
    }
    return intensity.asState()
}

internal val PomodoroBackground = Color(0xFF08090C)
internal val PomodoroAppearance.light: Color get() = when (this) {
    PomodoroAppearance.SOLAR -> Color(0xFFFFB347)
    PomodoroAppearance.NIGHT_BLUE -> Color(0xFF649CFF)
    PomodoroAppearance.SOFT_GREEN -> Color(0xFF7CC99A)
    PomodoroAppearance.MINIMAL -> Color(0xFFA6A8AD)
}

/** Cache the gradient; breathing reads opacity only while drawing, independently of timer ticks. */
@Composable
internal fun PomodoroDial(appearance: PomodoroAppearance, time: String, phase: String,
    progress: () -> Float, modifier: Modifier = Modifier, preview: Boolean = false, animateHalo: Boolean = true) {
    val greenBash=!preview && LocalAppearancePreferences.current?.settings?.bashTheme==true
    val palette=LocalAppearancePalette.current
    val color = appearance.light
    val breathing = animateHalo && !preview && appearance != PomodoroAppearance.MINIMAL && LocalAudioFooterVisible.current
    val intensity = pomodoroHaloIntensity(breathing)
    BoxWithConstraints(modifier.drawWithCache {
        val haloRadius = (size.minDimension * .66f).coerceAtLeast(1f)
        val halo = Brush.radialGradient(
            0f to color.copy(alpha = .43f), .5f to color.copy(alpha = .16f),
            1f to color.copy(alpha = 0f),
            center = Offset(size.width / 2f, size.height / 2f), radius = haloRadius)
        onDrawBehind {
            if (appearance != PomodoroAppearance.MINIMAL) {
                if (breathing) {
                    val opacity = intensity.value
                    val radiusScale = .90f + .15f * ((opacity - .35f) / .65f).coerceIn(0f, 1f)
                    // Transform only the cached halo; clip to its original drawing area.
                    clipRect {
                        scale(radiusScale, pivot = center) {
                            drawCircle(halo, radius = haloRadius, alpha = opacity)
                        }
                    }
                } else drawRect(halo)
            }
        }
    }, contentAlignment = Alignment.Center) {
        val diameter = minOf(maxWidth * .82f, maxHeight * .92f, 340.dp)
        val fontScale = LocalDensity.current.fontScale
        Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = progress,
                modifier = Modifier.fillMaxSize().then(if (preview) Modifier else Modifier.testTag("pomodoro-circle")),
                strokeWidth = if (preview) 2.dp else 4.dp, color = color, trackColor = if(greenBash) palette.outline else Color(0xFF34373D))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(time, color = if(greenBash) palette.text else Color.White, fontWeight = FontWeight.Light,
                    fontSize = (diameter.value * .205f / fontScale).sp, maxLines = 1,
                    modifier = if (preview) Modifier else Modifier.testTag("pomodoro-time"))
                if (!preview) Text(phase, color = if(greenBash) palette.muted else Color(0xFFB4B4B8),
                    fontSize = (diameter.value * .06f / fontScale).coerceAtMost(16f).sp, maxLines = 1)
            }
        }
    }
}
