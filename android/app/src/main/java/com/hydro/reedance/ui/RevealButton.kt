package com.hydro.reedance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/** The eye and label describe the next action; state is exposed separately to accessibility. */
@Composable internal fun RevealButton(revealed: Boolean, onClick: () -> Unit, meaning: Boolean = false) {
    val label = if(meaning) {
        if(revealed) tr("پنهان کردن معنی", "Hide meaning") else tr("نمایش معنی", "Reveal meaning")
    } else {
        if(revealed) tr("پنهان کردن پاسخ", "Hide answer") else tr("دیدن پاسخ", "Reveal answer")
    }
    val state = if(revealed) tr("نمایان", "Revealed") else tr("پنهان", "Hidden")
    PressTextButton(onClick, Modifier.semantics { stateDescription = state }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(if(revealed) UiIcons.EyeClosed else UiIcons.Eye, null, Modifier.size(18.dp))
            Text(label)
        }
    }
}
