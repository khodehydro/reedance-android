package com.hydro.reedance.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.sin

private val LocalHeartBurst = staticCompositionLocalOf<(Offset) -> Unit> { {} }

@Composable fun DeveloperCredit() {
    val burst = LocalHeartBurst.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    Text("developed with ❤️ by HYDRO team", style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.onGloballyPositioned { origin = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f) }
            .clickable(onClickLabel = tr("قلب‌ها را بفرست", "Send hearts")) { burst(origin) })
}

/** Root-level draw overlay: particles don't clip at a scrolling footer or take touches. */
@Composable fun HeartOverlay(content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(1f) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var job by remember { mutableStateOf<Job?>(null) }
    val heart = remember { Path().apply {
        moveTo(0f, 5f); cubicTo(-20f, -9f, -11f, -22f, 0f, -12f)
        cubicTo(11f, -22f, 20f, -9f, 0f, 5f); close()
    } }
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalHeartBurst provides { point ->
            job?.cancel()
            origin = point
            job = scope.launch { progress.snapTo(0f); progress.animateTo(1f, tween(if(reduced) 400 else 2400)) }
        }, content = content)
        Canvas(Modifier.matchParentSize()) {
            val p = progress.value
            if(p < 1f) repeat(if(reduced) 1 else 12) { index ->
                val t = ((p - index * .025f) / (1f - index * .025f)).coerceIn(0f, 1f)
                if(t > 0f && t < 1f) {
                    val x = (origin.x + if(reduced) 0f else (sin(t * 7f + index) - sin(index.toFloat())) * (16 + index * 3) * density).coerceIn(16f, size.width - 16f)
                    val y = if(reduced) origin.y else origin.y - t * (origin.y + 40f * density)
                    translate(x, y) { scale(density * (.7f + (index % 3) * .12f), pivot = Offset.Zero) {
                        drawPath(heart, Color(0xFFFF4778), alpha = (1f - t).coerceIn(0f, 1f))
                    } }
                }
            }
        }
    }
}
