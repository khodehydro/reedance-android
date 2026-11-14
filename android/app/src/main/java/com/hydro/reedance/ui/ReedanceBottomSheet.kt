package com.hydro.reedance.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val SheetBlue = Color(0xFF062FFD)

/** Shared sheet palette in light/dark mode, including buttons and radio controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ReedanceBottomSheet(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme.copy(
        primary = Color.White,
        onPrimary = SheetBlue,
        secondary = Color.White,
        onSecondary = SheetBlue,
        surface = SheetBlue,
        surfaceContainerLow = SheetBlue,
        onSurface = Color.White,
        onSurfaceVariant = Color.White,
        outline = Color.White.copy(alpha = .7f)
    )
    MaterialTheme(colorScheme = colors) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            containerColor = SheetBlue,
            contentColor = Color.White,
            tonalElevation = 0.dp,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = .65f)) },
            content = content
        )
    }
}
