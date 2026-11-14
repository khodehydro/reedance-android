package com.hydro.reedance.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.sin

/** Native Slider retains touch, keyboard and accessibility seeking; only its track is custom. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun WaveSeekBar(value: Float, duration: Float, playing: Boolean,
    onValueChange: (Float) -> Unit, onValueChangeFinished: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val paused = LocalBackgroundMotion.current.paused
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing, reduced, paused, lifecycle) {
        if(playing && !reduced && !paused) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previous = 0L
            while(isActive) withFrameNanos { frame ->
                if(previous == 0L) previous = frame
                if(frame - previous >= 32_000_000L) {
                    phase = (phase + (frame - previous) / 1_000_000_000f * 3f) % (2f * PI.toFloat())
                    previous = frame
                }
            }
        }
    }
    val description = tr("موقعیت پخش", "Playback position")
    val maximum = duration.coerceAtLeast(1f)
    Slider(value = value.coerceIn(0f, maximum), onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished, valueRange = 0f..maximum,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = description },
        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = .45f)),
        thumb = { Spacer(Modifier.size(0.dp)) },
        track = { state ->
            Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                val end = size.width * (state.value / maximum).coerceIn(0f, 1f)
                val center = size.height / 2f
                val stroke = 2.dp.toPx()
                drawLine(Color.White.copy(alpha = .45f), Offset(end, center), Offset(size.width, center), stroke, StrokeCap.Round)
                if(end > 0f) {
                    val path = Path()
                    val wavelength = 20.dp.toPx()
                    val amplitude = 3.dp.toPx()
                    val steps = (end / 2.dp.toPx()).toInt().coerceAtLeast(1)
                    for(step in 0..steps) {
                        val x = end * step / steps
                        // Taper both ends so the wave meets the straight track cleanly.
                        val taper = minOf(x / wavelength, (end - x) / wavelength, 1f).coerceAtLeast(0f)
                        val y = center + sin(x / wavelength * 2f * PI.toFloat() - phase) * amplitude * taper
                        if(step == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, Color.White, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
        })
}
