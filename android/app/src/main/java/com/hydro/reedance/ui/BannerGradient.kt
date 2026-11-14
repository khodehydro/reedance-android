package com.hydro.reedance.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive
import kotlin.math.sin

/** Draw-only, slowly wandering gradient fields. No frame-by-frame text/layout work. */
@Composable internal fun BannerGradient(animate: Boolean, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var seconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animate, reduced, lifecycle) {
        if(reduced) seconds = 0f
        if(animate && !reduced) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previous = 0L
            while(isActive) withFrameNanos { frame ->
                if(previous != 0L) seconds += ((frame - previous) / 1_000_000_000f).coerceAtMost(.1f)
                previous = frame
            }
        }
    }
    Canvas(modifier) {
        val t = seconds // Read in draw, not composition.
        val first = Offset(
            size.width * (.5f + .27f * sin(t * .19f) + .12f * sin(t * .43f + 1.2f)),
            size.height * (.5f + .28f * sin(t * .23f + 2f) + .13f * sin(t * .37f))
        )
        val second = Offset(
            size.width * (.5f + .3f * sin(t * .17f + 3f) + .11f * sin(t * .31f)),
            size.height * (.5f + .25f * sin(t * .29f) + .14f * sin(t * .41f + 2f))
        )
        drawRect(Brush.linearGradient(listOf(Color(0xFF173F80), Deep), start = first, end = second + Offset(size.width * .55f, size.height * .4f)))
        drawRect(Brush.radialGradient(listOf(Color(0x992B65C5), Color.Transparent), center = first, radius = size.maxDimension * .85f))
        drawRect(Brush.radialGradient(listOf(Color(0x664B36A0), Color.Transparent), center = second, radius = size.maxDimension * .75f))
    }
}
