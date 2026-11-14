package com.hydro.reedance.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.hydro.reedance.R
import kotlinx.coroutines.isActive

/** Small draw-layer drift; padding keeps ears/headphones clear of parent clipping. */
@Composable internal fun StarterArtwork(modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val shift = remember { Animatable(0f) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(visible, reduced, lifecycle) {
        if(reduced) shift.snapTo(0f)
        if(visible && !reduced) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(isActive) {
                shift.animateTo(-4f, tween(2400, easing = FastOutSlowInEasing))
                shift.animateTo(4f, tween(2400, easing = FastOutSlowInEasing))
            }
        }
    }
    Image(painterResource(R.drawable.reedance_starter), contentDescription = null,
        modifier = modifier.onGloballyPositioned {
            val bounds = it.boundsInWindow()
            visible = bounds.width > 0f && bounds.height > 0f
        }.padding(vertical = 6.dp).graphicsLayer { translationY = shift.value.dp.toPx() },
        contentScale = ContentScale.Fit)
}
