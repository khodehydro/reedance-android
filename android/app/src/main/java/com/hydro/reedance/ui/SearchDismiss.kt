package com.hydro.reedance.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

@Stable internal class SearchFocus(val dismiss: () -> Unit) {
    var focused by mutableStateOf(false)
    var bounds by mutableStateOf(Rect.Zero)
}
internal val LocalSearchFocus = staticCompositionLocalOf<SearchFocus?> { null }

/** Observe outside downs, including toolbar/nav, without consuming scrolls or clicks. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun SearchDismissHost(content: @Composable () -> Unit) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val session = remember(focus, keyboard) { SearchFocus { focus.clearFocus(force = true); keyboard?.hide() } }
    // Android may let IME consume Back before BackHandler. Clear the field's
    // focus when that dismissal finishes, so the next Back navigates normally.
    val imeVisible = WindowInsets.isImeVisible
    var imeWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if(imeWasVisible && !imeVisible && session.focused) session.dismiss()
        imeWasVisible = imeVisible
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    BackHandler(session.focused) { session.dismiss() }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }.pointerInput(session) {
        awaitPointerEventScope {
            while(true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val down = event.changes.firstOrNull { it.changedToDownIgnoreConsumed() }
                if(down != null && session.focused && !session.bounds.contains(down.position + origin)) session.dismiss()
            }
        }
    }) {
        CompositionLocalProvider(LocalSearchFocus provides session, content = content)
    }
}
