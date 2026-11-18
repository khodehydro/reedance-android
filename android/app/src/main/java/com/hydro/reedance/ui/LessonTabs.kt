package com.hydro.reedance.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** One measured navigation surface: the connector never intercepts touches or exposes semantics. */
@Composable internal fun LessonTabs(tab: Int, onTab: (Int) -> Unit, mode: Int, onMode: (Int) -> Unit,
    single: Boolean, onLayout: () -> Unit, onQuiz: () -> Unit, onStory: (() -> Unit)?) {
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
    var topHeight by remember { mutableIntStateOf(0) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val ink = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxWidth()) {
        if(tab == 0) Canvas(Modifier.matchParentSize()) {
            val gap = 20.dp.toPx()
            val toggle = 48.dp.toPx()
            fun physical(x: Float) = if(rtl) size.width - x else x
            val x1 = physical(size.width / 10f)
            val x2 = physical((size.width - toggle) / 2f)
            val y1 = topHeight.toFloat() - 12.dp.toPx()
            val y2 = topHeight + gap + 12.dp.toPx()
            val neck = 5.dp.toPx()
            val end = 15.dp.toPx()
            val middle = (y1 + y2) / 2f
            val path = Path().apply {
                moveTo(x1 - end, y1)
                cubicTo(x1 - neck, middle, x2 - neck, middle, x2 - end, y2)
                lineTo(x2 + end, y2)
                cubicTo(x2 + neck, middle, x1 + neck, middle, x1 + end, y1)
                close()
            }
            drawPath(path, ink)
        }
        Column {
            Row(Modifier.fillMaxWidth().onSizeChanged { topHeight = it.height }.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                tabs.forEachIndexed { index, option ->
                    LessonTab(option, tab == index, Modifier.weight(1f)) { if(index == 4) onQuiz() else onTab(index) }
                }
            }
            if(tab == 0) {
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val current = modes[mode]
                    PressButton(
                        onClick = { onMode((mode + 1) % modes.size) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        (current.filledIcon ?: current.icon)?.let { AppIcon(it, null, Modifier.size(18.dp)) }
                        Spacer(Modifier.width(8.dp))
                        Text(tr("نمایش متن: ", "Lyrics: ") + current.label, textAlign = TextAlign.Center)
                    }
                    Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) { LyricLayoutToggle(single, onLayout) }
                }
            }
            if(onStory != null) {
                PressOutlinedButton(onStory, Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    AppIcon(UiIcons.Library, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("داستان آهنگ", "Song story"))
                }
            }
        }
    }
}

@Composable private fun LessonTab(option: PillOption, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier.selectable(selected, role = Role.Tab, onClick = onClick), shape = RoundedCornerShape(18.dp),
        color = if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if(selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) {
        Column(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 3.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)) {
            (if(selected) option.filledIcon ?: option.icon else option.icon)?.let { AppIcon(it, null, Modifier.size(18.dp)) }
            Text(option.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                fontWeight = if(selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}
