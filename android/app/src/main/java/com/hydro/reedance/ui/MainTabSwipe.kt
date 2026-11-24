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

/** An Explore swipe either stops on its other segment or exits at the appropriate edge. */
internal fun explorePageForMainSwipe(from: String, to: String, currentExplorePage: Int): Int? = when {
    from == "explore" && to == StudyHubRoute && currentExplorePage == 0 -> 1
    from == "explore" && to == "home" && currentExplorePage == 1 -> 0
    from == "home" && to == "explore" -> 0
    from == StudyHubRoute && to == "explore" -> 1
    else -> null
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
