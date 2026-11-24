package com.hydro.reedance.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal val mainTabRoutes = listOf("home", "explore", StudyHubRoute)

internal fun mainTabAfterSwipe(page: String, dragLeft: Boolean, direction: LayoutDirection): String? {
    val index = mainTabRoutes.indexOf(page)
    if(index < 0) return null
    val next = dragLeft == (direction == LayoutDirection.Ltr)
    return mainTabRoutes.getOrNull(index + if(next) 1 else -1)
}

internal fun Modifier.mainTabSwipe(page: String, enabled: Boolean, direction: LayoutDirection,
    navigate: (String) -> Unit): Modifier {
    if(!enabled) return this
    return pointerInput(page, direction, navigate) {
        val threshold = 48.dp.toPx()
        var distance = 0f
        // Listen in the normal child-first pass so carousels keep their own drags.
        detectHorizontalDragGestures(
            onDragStart = { distance = 0f },
            onHorizontalDrag = { _, delta -> distance += delta },
            onDragEnd = {
                if(kotlin.math.abs(distance) >= threshold) {
                    mainTabAfterSwipe(page, distance < 0f, direction)?.let(navigate)
                }
            }
        )
    }
}
