package com.hydro.reedance.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Two measured strips, one fixed-height slot. Animation reads occur only in render layers. */
@Composable internal fun CollapsingPillTabs(items: List<PillOption>, selected: Int, compact: Boolean, onSelect: (Int) -> Unit) {
    val progress = remember { Animatable(if(compact) 1f else 0f) }
    val reduced = LocalReducedMotion.current
    LaunchedEffect(compact, reduced) {
        if(reduced) progress.snapTo(if(compact) 1f else 0f)
        else progress.animateTo(if(compact) 1f else 0f, tween(180))
    }
    Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
        LibraryTabStrip(items, selected, false, !compact, Modifier.graphicsLayer {
            alpha = 1f - progress.value
            translationY = -4.dp.toPx() * progress.value
        }, onSelect)
        LibraryTabStrip(items, selected, true, compact, Modifier.graphicsLayer {
            alpha = progress.value
            translationY = 4.dp.toPx() * (1f - progress.value)
        }, onSelect)
    }
}

@Composable private fun LibraryTabStrip(items: List<PillOption>, selected: Int, iconsOnly: Boolean,
    interactive: Boolean, modifier: Modifier, onSelect: (Int) -> Unit) {
    val semantics = if(interactive) Modifier else Modifier.clearAndSetSemantics { }
    Row(modifier.then(semantics).horizontalScroll(rememberScrollState(), enabled = interactive).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEachIndexed { index, item ->
            val active = selected == index
            Surface(Modifier.selectable(active, enabled = interactive, role = Role.Tab, onClick = { onSelect(index) })
                .semantics { contentDescription = item.description }, shape = CircleShape,
                color = if(active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if(active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.heightIn(min = 48.dp).then(if(iconsOnly) Modifier.width(48.dp) else Modifier.padding(horizontal = 14.dp)),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally)) {
                    (if(active) item.filledIcon ?: item.icon else item.icon)?.let { AppIcon(it, null, Modifier.size(18.dp)) }
                    if(!iconsOnly) Text(item.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}
