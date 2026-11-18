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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private data class Destination(val id: String, val label: String, val outline: Int, val filled: Int)

internal val navigationMenuHorizontalInset = 12.dp
internal fun navigationMenuWidth(compact: Boolean) = if(compact) 270.dp else 344.dp

@Composable fun FloatingNavigation(
    page: String,
    compact: Boolean,
    onNavigate: (String) -> Unit,
    menuWidth: Dp = navigationMenuWidth(compact)
) {
    val duration = if(LocalReducedMotion.current) 0 else 150
    val destinations = listOf(
        Destination("home", tr("خانه", "Home"), UiIcons.Home, UiIcons.HomeFilled),
        Destination("explore", tr("کشف", "Explore"), UiIcons.Explore, UiIcons.ExploreFilled),
        Destination("game", tr("گیم", "Game"), UiIcons.Quiz, UiIcons.Quiz),
        Destination("review", tr("لایتنر", "Review"), UiIcons.Review, UiIcons.ReviewFilled),
        Destination("library", tr("کتابخانه", "Library"), UiIcons.Library, UiIcons.LibraryFilled)
    )

    // A wider bounded slot leaves room for the selected label throughout the
    // width animation, so text and rounded corners never get cropped mid-transition.
    Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = navigationMenuHorizontalInset), contentAlignment = Alignment.BottomCenter) {
        Surface(
            modifier = Modifier.widthIn(max = menuWidth).fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 3.dp,
            tonalElevation = 0.dp
        ) {
            Row(
                Modifier.fillMaxWidth().selectableGroup().padding(5.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                destinations.forEach { destination ->
                    val active = page == destination.id
                    val chipColor by animateColorAsState(
                        if(active) Blue else Color.Transparent,
                        tween(duration),
                        label = "nav-selected-color-${destination.id}"
                    )
                    val tabShape = if(compact) CircleShape else RoundedCornerShape(24.dp)
                    Row(
                        Modifier.clip(tabShape)
                            .background(chipColor)
                            .selectable(active, role = Role.Tab, onClick = { onNavigate(destination.id) })
                            .semantics { contentDescription = destination.label }
                            .height(48.dp)
                            .padding(horizontal = 12.dp),
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
