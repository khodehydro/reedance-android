package com.hydro.reedance.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp

internal val LocalSheetDismiss = staticCompositionLocalOf<((() -> Unit) -> Unit)> { { action -> action() } }

/** Shared sheet palette in light/dark mode, including buttons and radio controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ReedanceBottomSheet(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val motion = LocalBackgroundMotion.current
    DisposableEffect(motion) {
        motion.overlays++
        onDispose { motion.overlays-- }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    val latestDismiss by rememberUpdatedState(onDismissRequest)
    val dismiss: (() -> Unit) -> Unit = { afterHidden ->
        if(!closing) {
            closing = true
            scope.launch {
                try {
                    sheetState.hide()
                    if(!sheetState.isVisible) {
                        afterHidden()
                        latestDismiss()
                    }
                } finally { closing = false }
            }
        }
    }
    val colors = MaterialTheme.colorScheme
    MaterialTheme(colorScheme = colors) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            containerColor = colors.surfaceContainerLow,
            contentColor = colors.onSurface,
            tonalElevation = 0.dp,
            dragHandle = { BottomSheetDefaults.DragHandle(color = colors.onSurfaceVariant) },
            content = {
                CompositionLocalProvider(LocalSheetDismiss provides dismiss) { content() }
            }
        )
    }
}
