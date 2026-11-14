package com.hydro.reedance.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/** Placement-only page entry: no full-window alpha layer around live RuntimeShaders. */
@Composable internal fun PageEntrance(destination: String, content: @Composable BoxScope.() -> Unit) {
    val reduced = LocalReducedMotion.current
    val progress = remember(destination) { Animatable(if(reduced) 1f else 0f) }
    val distance = with(LocalDensity.current) { 12.dp.toPx() }
    LaunchedEffect(destination, reduced) {
        if(reduced) progress.snapTo(1f) else progress.animateTo(1f, tween(230, easing = FastOutSlowInEasing))
    }
    Box(Modifier.fillMaxSize().offset {
        IntOffset(0, (distance * (1f - progress.value)).roundToInt())
    }, content = content)
}

/** Lazy keys/saveable slots prevent a card replaying its entrance on every recomposition. */
@Composable internal fun Modifier.itemEntrance(): Modifier {
    val reduced = LocalReducedMotion.current
    var entered by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if(entered || reduced) 1f else 0f) }
    val distance = with(LocalDensity.current) { 8.dp.toPx() }
    LaunchedEffect(reduced) {
        if(reduced) progress.snapTo(1f) else progress.animateTo(1f, tween(200, easing = FastOutSlowInEasing))
        entered = true
    }
    return graphicsLayer {
        compositingStrategy = CompositingStrategy.ModulateAlpha
        alpha = .75f + .25f * progress.value
        translationY = distance * (1f - progress.value)
    }
}
