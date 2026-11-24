package com.hydro.reedance.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private data class Destination(val id: String, val label: String, val outline: Int, val filled: Int)

internal val navigationMenuHorizontalInset = 12.dp
internal fun navigationMenuWidth(compact: Boolean) = if(compact) 282.dp else 368.dp

// Apply the same fixed width to the mini-player and tab bar for each mode.
internal fun Modifier.navigationBarWidth(width: () -> Dp): Modifier = layout { measurable, constraints ->
    val px = width().roundToPx().coerceIn(constraints.minWidth, constraints.maxWidth)
    val child = measurable.measure(constraints.copy(minWidth = px, maxWidth = px))
    layout(child.width, child.height) { child.placeRelative(0, 0) }
}

@Composable fun FloatingNavigation(
    page: String,
    compact: Boolean,
    onNavigate: (String) -> Unit,
    menuWidth: () -> Dp = { navigationMenuWidth(compact) }
) {
    val reduced = LocalReducedMotion.current
    val destinations = listOf(
        Destination("home", tr("خانه", "Home"), UiIcons.Home, UiIcons.HomeFilled),
        Destination("explore", tr("کشف", "Explore"), UiIcons.Explore, UiIcons.ExploreFilled),
        Destination("review", tr("لایتنر", "Review"), UiIcons.Review, UiIcons.ReviewFilled),
        Destination("gym", tr("تمرین", "Gym"), UiIcons.Dumbbell, UiIcons.DumbbellFilled),
        Destination("game", tr("گیم", "Game"), UiIcons.Game, UiIcons.GameFilled),
        Destination("library", tr("کتابخانه", "Library"), UiIcons.Library, UiIcons.LibraryFilled)
    )

    // Geometry is reset (not animated) when changing between compact and expanded tabs.
    val bounds = remember(compact) { mutableStateMapOf<String, Rect>() }
    val leading = remember(compact) { Animatable(0f) }
    val trailing = remember(compact) { Animatable(0f) }
    var initialized by remember(compact) { mutableStateOf(false) }
    val target = bounds[page]
    LaunchedEffect(leading, trailing, target, reduced) {
        if(target == null) return@LaunchedEffect
        if(!initialized || reduced) {
            leading.snapTo(target.left)
            trailing.snapTo(target.right)
            initialized = true
        } else {
            val movingRight = target.center.x >= (leading.value + trailing.value) / 2f
            coroutineScope {
                launch { leading.animateTo(target.left, spring(dampingRatio = .90f, stiffness = if(movingRight) 290f else 620f)) }
                launch { trailing.animateTo(target.right, spring(dampingRatio = .90f, stiffness = if(movingRight) 620f else 290f)) }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = navigationMenuHorizontalInset), contentAlignment = Alignment.BottomCenter) {
        val compactSize = minOf(44.dp, ((minOf(maxWidth, navigationMenuWidth(true)) - 10.dp) / destinations.size).coerceAtLeast(0.dp))
        Surface(
            modifier = Modifier.navigationBarWidth(menuWidth),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .96f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 3.dp,
            tonalElevation = 0.dp
        ) {
            // The indicator and the tab bounds share the unpadded row's coordinates.
            Box(Modifier.padding(5.dp)) {
                Row(
                    Modifier.fillMaxWidth().drawBehind {
                        val tab = bounds[page] ?: return@drawBehind
                        val left = (if(initialized) leading.value else tab.left).coerceIn(0f, size.width)
                        val right = (if(initialized) trailing.value else tab.right).coerceIn(left, size.width)
                        drawRoundRect(color = Blue, topLeft = Offset(left, (size.height - tab.height) / 2f), size = Size(right - left, tab.height), cornerRadius = CornerRadius(tab.height / 2f))
                    }.selectableGroup(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    destinations.forEach { destination ->
                        val active = page == destination.id
                        val selectedInk by remember(active, bounds, leading, trailing, destination.id) {
                            derivedStateOf {
                                val tab = bounds[destination.id]
                                active && (!initialized || tab == null ||
                                    (leading.value <= tab.left + tab.width * .10f && trailing.value >= tab.right - tab.width * .10f))
                            }
                        }
                        val tabShape = if(compact) CircleShape else RoundedCornerShape(24.dp)
                        Row(
                            Modifier.onGloballyPositioned { coordinates ->
                                val point = coordinates.positionInParent()
                                bounds[destination.id] = Rect(point, Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
                            }.clip(tabShape)
                                .selectable(active, role = Role.Tab, onClick = { onNavigate(destination.id) })
                                .semantics { contentDescription = destination.label }
                                .then(if(compact) Modifier.size(compactSize) else Modifier.height(48.dp).padding(horizontal = if(active) 12.dp else 6.dp)),
                            horizontalArrangement = if(compact) Arrangement.Center else Arrangement.spacedBy(7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val contentColor = if(selectedInk) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            AppIcon(
                                if(active) destination.filled else destination.outline,
                                null,
                                Modifier.size(22.dp),
                                contentColor
                            )
                            if(active && !compact) Text(
                                destination.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
