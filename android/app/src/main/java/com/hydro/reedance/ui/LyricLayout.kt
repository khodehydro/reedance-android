package com.hydro.reedance.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.google.gson.JsonObject
import com.hydro.reedance.data.*

/** Follow the media clock, not an independent timer; hold the last line through musical gaps. */
internal fun timedLyricLine(lines: List<JsonObject>, positionMs: Long): JsonObject? =
    lines.lastOrNull { it.n("startMs") <= positionMs } ?: lines.firstOrNull()

@Composable internal fun LyricLayoutToggle(single: Boolean, onClick: () -> Unit) {
    val state = if(single) tr("نمایش تکی لیریک", "Single lyric view") else tr("نمایش لیستی لیریک", "Lyrics list view")
    PressIconButton(onClick, Modifier.semantics { stateDescription = state }) {
        AppIcon(if(single) UiIcons.Lines else UiIcons.Notes,
            if(single) tr("نمایش لیستی لیریک", "Show lyrics list") else tr("نمایش تکی لیریک", "Show single lyric"))
    }
}
