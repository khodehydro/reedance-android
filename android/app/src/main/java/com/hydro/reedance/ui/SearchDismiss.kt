package com.hydro.reedance.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

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

/** Shared Explore/Library search field, including the same clear and IME behavior. */
@Composable internal fun CatalogSearchField(query: String, onQuery: (String) -> Unit, label: String,
    visible: Boolean = true) {
    val session = LocalSearchFocus.current
    val active = LocalPageActive.current && visible
    var fieldFocused by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    LaunchedEffect(active) { if(!active && fieldFocused) session?.dismiss?.invoke() }
    DisposableEffect(session) {
        onDispose { if(fieldFocused) session?.dismiss?.invoke() }
    }
    OutlinedTextField(
        value = query, onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth()
            .onFocusChanged {
                val wasFocused = fieldFocused
                fieldFocused = it.isFocused
                if(session != null && (it.isFocused || wasFocused)) {
                    session.focused = it.isFocused
                    if(it.isFocused) session.bounds = bounds
                }
            }
            .onGloballyPositioned {
                bounds = it.boundsInRoot()
                if(fieldFocused) session?.bounds = bounds
            },
        shape = RoundedCornerShape(20.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { session?.dismiss?.invoke() }),
        leadingIcon = { AppIcon(UiIcons.Search, null, Modifier.size(20.dp)) },
        singleLine = true, label = { Text(label) },
        trailingIcon = { if(query.isNotEmpty()) PressTextButton({ onQuery("") }) { Text(tr("پاک کردن", "Clear")) } }
    )
}
