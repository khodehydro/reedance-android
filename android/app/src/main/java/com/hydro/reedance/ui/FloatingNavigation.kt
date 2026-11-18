package com.hydro.reedance.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.animateContentSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private data class Destination(val id: String, val label: String, val outline: Int, val filled: Int)

@Composable fun FloatingNavigation(page: String, compact: Boolean, onNavigate: (String) -> Unit) {
    val duration = if(LocalReducedMotion.current) 0 else 190
    val destinations = listOf(
        Destination("home", tr("خانه", "Home"), UiIcons.Home, UiIcons.HomeFilled),
        Destination("explore", tr("کشف", "Explore"), UiIcons.Explore, UiIcons.ExploreFilled),
        Destination("review", tr("لایتنر", "Review"), UiIcons.Review, UiIcons.ReviewFilled),
        Destination("library", tr("کتابخانه", "Library"), UiIcons.Library, UiIcons.LibraryFilled)
    )
    val slotHeight = 56.dp

    // The outer surface is the only navigation background. Each destination is
    // otherwise just an icon; the selected item grows into a blue icon + label.
    Box(Modifier.fillMaxWidth().heightIn(min = slotHeight).padding(horizontal = 12.dp), contentAlignment = Alignment.BottomCenter) {
        Surface(
            modifier = Modifier.animateContentSize(tween(duration)).wrapContentWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 3.dp,
            tonalElevation = 0.dp
        ) {
            Row(
                Modifier.selectableGroup().padding(horizontal = 5.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                destinations.forEach { destination ->
                    val active = page == destination.id
                    val tabShape = if(compact) CircleShape else RoundedCornerShape(24.dp)
                    val tabModifier = Modifier
                        .clip(tabShape)
                        .then(if(active) Modifier.background(Blue) else Modifier)
                        .selectable(active, role = Role.Tab, onClick = { onNavigate(destination.id) })
                        .semantics { contentDescription = destination.label }
                        .animateContentSize(tween(duration))
                        .height(48.dp)
                        .padding(horizontal = 13.dp)
                    Row(
                        tabModifier,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(
                            if(active) destination.filled else destination.outline,
                            null,
                            Modifier.size(22.dp),
                            if(active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AnimatedVisibility(
                            visible = active && !compact,
                            enter = fadeIn(tween(duration)) + expandHorizontally(tween(duration), clip = false),
                            exit = fadeOut(tween(duration)) + shrinkHorizontally(tween(duration), clip = false)
                        ) {
                            Text(
                                destination.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
