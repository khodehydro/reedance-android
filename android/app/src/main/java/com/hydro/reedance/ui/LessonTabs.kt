package com.hydro.reedance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Shared tile styling for playback tabs and their sibling actions. */
@Composable internal fun LessonTabs(tab: Int, onTab: (Int) -> Unit, mode: Int, onMode: (Int) -> Unit,
    single: Boolean, onLayout: () -> Unit, onQuiz: () -> Unit, onStory: (() -> Unit)?, compact: Boolean = false) {
    val tabs = listOf(
        PillOption(tr("لیریک", "Lyrics"), UiIcons.Lyrics, filledIcon = UiIcons.LyricsFilled),
        PillOption(tr("نکته‌ها", "Notes"), UiIcons.Notes, filledIcon = UiIcons.NotesFilled),
        PillOption(tr("لغت‌ها", "Words"), UiIcons.Words, filledIcon = UiIcons.WordsFilled),
        PillOption(tr("عبارت‌ها", "Phrases"), UiIcons.Phrases, filledIcon = UiIcons.PhrasesFilled),
        PillOption(tr("آزمون", "Song quiz"), UiIcons.Quiz)
    )
    val modes = listOf(
        PillOption(tr("متن اصلی", "Original"), UiIcons.Original, filledIcon = UiIcons.OriginalFilled),
        PillOption(tr("ترجمهٔ خطی", "Line by line"), UiIcons.Lines, filledIcon = UiIcons.LinesFilled),
        PillOption(tr("کلمه‌به‌کلمه", "Word by word"), UiIcons.WordMode, filledIcon = UiIcons.WordModeFilled)
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tabs.forEachIndexed { index, option ->
                val quiz = index == tabs.lastIndex
                LessonTab(option, !quiz && tab == index, Modifier.weight(1f), action = quiz, compact = compact) {
                    if(quiz) onQuiz() else onTab(index)
                }
            }
            if(onStory != null) {
                LessonTab(PillOption(tr("داستان", "Song story"), UiIcons.Headphones), false, Modifier.weight(1f),
                    action = true, compact = compact, onClick = onStory)
            }
        }
        if(tab == 0 && !compact) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val current = modes[mode]
                LessonTab(current.copy(label = tr("نمایش متن: ", "Lyrics: ") + current.label),
                    false, Modifier.weight(1f), action = true) {
                    onMode((mode + 1) % modes.size)
                }
                Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) { LyricLayoutToggle(single, onLayout) }
            }
        }
    }
}

@Composable private fun LessonTab(option: PillOption, selected: Boolean, modifier: Modifier, action: Boolean = false, compact: Boolean = false, onClick: () -> Unit) {
    val interaction = if(action) Modifier.clickable(role = Role.Button, onClick = onClick)
        else Modifier.selectable(selected, role = Role.Tab, onClick = onClick)
    Surface(modifier.then(interaction).then(if(compact) Modifier.semantics { contentDescription = option.label } else Modifier), shape = RoundedCornerShape(18.dp),
        color = if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if(selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) {
        Column(Modifier.fillMaxWidth().heightIn(min = if(compact) 48.dp else 72.dp).padding(horizontal = 3.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)) {
            (if(selected) option.filledIcon ?: option.icon else option.icon)?.let { AppIcon(it, null, Modifier.size(18.dp)) }
            if(!compact) Text(option.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                fontWeight = if(selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}
