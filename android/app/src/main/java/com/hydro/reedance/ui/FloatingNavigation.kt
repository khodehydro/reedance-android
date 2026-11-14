package com.hydro.reedance.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private data class Destination(val id: String, val label: String, val outline: Int, val filled: Int)

@Composable fun FloatingNavigation(page: String, compact: Boolean, onNavigate: (String) -> Unit) {
    val duration = if(LocalReducedMotion.current) 0 else 190
    val inset by animateDpAsState(if(compact) 52.dp else 20.dp, tween(duration), label = "nav-width")
    val destinations = listOf(
        Destination("home", tr("خانه", "Home"), UiIcons.Home, UiIcons.HomeFilled),
        Destination("explore", tr("کشف", "Explore"), UiIcons.Explore, UiIcons.ExploreFilled),
        Destination("review", tr("لایتنر", "Review"), UiIcons.Review, UiIcons.ReviewFilled),
        Destination("library", tr("کتابخانه", "Library"), UiIcons.Library, UiIcons.LibraryFilled)
    )
    val slotHeight = with(LocalDensity.current) { 56.dp + MaterialTheme.typography.labelSmall.lineHeight.toDp() }
    // Keep the Scaffold bottom slot stable while the capsule animates. Otherwise
    // its changing height updates every page's trailing inset on every frame.
    Box(Modifier.fillMaxWidth().heightIn(min = slotHeight).padding(horizontal = inset), contentAlignment = Alignment.BottomCenter) {
        Surface(shape = CircleShape, color = Color(0xFF0A2EFE), contentColor = Color.White, shadowElevation = 2.dp, tonalElevation = 0.dp) {
            Row(Modifier.fillMaxWidth().selectableGroup().padding(4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                destinations.forEach { destination ->
                    val active = page == destination.id
                    val tint = Color.White
                    Column(Modifier.weight(1f).clip(CircleShape)
                        .selectable(active, role = Role.Tab, onClick = { onNavigate(destination.id) })
                        .semantics { contentDescription = destination.label }
                        .heightIn(min = 48.dp).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                            AppIcon(if(active) destination.filled else destination.outline, null, Modifier.size(22.dp), tint)
                        }
                        AnimatedVisibility(!compact,
                            enter = fadeIn(tween(duration)) + expandVertically(tween(duration)),
                            exit = fadeOut(tween(duration)) + shrinkVertically(tween(duration))) {
                            Text(destination.label, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
