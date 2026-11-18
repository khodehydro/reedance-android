package com.hydro.reedance.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

private data class Destination(val id: String, val label: String, val outline: Int, val filled: Int)

@OptIn(ExperimentalHazeApi::class)
@Composable fun FloatingNavigation(page: String, compact: Boolean, hazeState: HazeState, onNavigate: (String) -> Unit) {
    val duration = if(LocalReducedMotion.current) 0 else 190
    val inset by animateDpAsState(if(compact) 52.dp else 20.dp, tween(duration), label = "nav-width")
    val destinations = listOf(
        Destination("home", tr("خانه", "Home"), UiIcons.Home, UiIcons.HomeFilled),
        Destination("explore", tr("کشف", "Explore"), UiIcons.Explore, UiIcons.ExploreFilled),
        Destination("review", tr("لایتنر", "Review"), UiIcons.Review, UiIcons.ReviewFilled),
        Destination("library", tr("کتابخانه", "Library"), UiIcons.Library, UiIcons.LibraryFilled)
    )
    val slotHeight = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp + MaterialTheme.typography.labelSmall.lineHeight.toDp() }
    // Keep the Scaffold bottom slot stable while the capsule animates. Otherwise
    // its changing height updates every page's trailing inset on every frame.
    Box(Modifier.fillMaxWidth().heightIn(min = slotHeight).padding(horizontal = inset), contentAlignment = Alignment.BottomCenter) {
        val dark = MaterialTheme.colorScheme.background.luminance() < .5f
        val glassShape = RoundedCornerShape(32.dp)
        val tint = if(dark) Color.White.copy(alpha = .26f) else Color.White.copy(alpha = .34f)
        val fallback = if(dark) Color(0xCC191D25) else Color(0xDDF5F7FA)
        val primaryGlass = if(dark) Color(0x481B2028) else Color(0x62F7F8FA)
        val secondaryGlass = if(dark) Color(0x38262D38) else Color(0x51E7EBF1)
        val foreground = if(dark) Color.White.copy(alpha = .96f) else Color(0xFF202630)
        val mutedForeground = if(dark) Color.White.copy(alpha = .72f) else Color(0xFF3D4652).copy(alpha = .78f)
        val glassStyle = HazeStyle(
            tints = listOf(HazeTint(tint)),
            blurRadius = 30.dp,
            noiseFactor = .10f,
            fallbackTint = HazeTint(fallback)
        )
        Box(
            Modifier.fillMaxWidth()
                .shadow(16.dp, glassShape, clip = false)
                .clip(glassShape)
                .hazeEffect(state = hazeState, style = glassStyle)
                .background(Brush.linearGradient(listOf(primaryGlass, secondaryGlass, primaryGlass)))
                .border(1.dp, Brush.verticalGradient(listOf(
                    Color.White.copy(alpha = if(dark) .42f else .88f),
                    Color.White.copy(alpha = if(dark) .16f else .50f),
                    foreground.copy(alpha = .12f)
                )), glassShape)
        ) {
            // Subtle specular highlight over the live, locally blurred backdrop.
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
                Color.White.copy(alpha = if(dark) .12f else .32f),
                Color.Transparent,
                foreground.copy(alpha = .04f)
            ))))
            Box(Modifier.fillMaxWidth().padding(horizontal = 26.dp).height(1.dp)
                .background(Color.White.copy(alpha = if(dark) .48f else .84f), CircleShape))
            Row(Modifier.fillMaxWidth().selectableGroup().padding(5.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                destinations.forEach { destination ->
                    val active = page == destination.id
                    val iconTint = if(active) foreground else mutedForeground
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(25.dp))
                        .background(if(active) foreground.copy(alpha = if(dark) .13f else .12f) else Color.Transparent)
                        .selectable(active, role = Role.Tab, onClick = { onNavigate(destination.id) })
                        .semantics { contentDescription = destination.label }
                        .heightIn(min = 48.dp).padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                            AppIcon(if(active) destination.filled else destination.outline, null, Modifier.size(22.dp), iconTint)
                        }
                        AnimatedVisibility(!compact,
                            enter = fadeIn(tween(duration)) + expandVertically(tween(duration)),
                            exit = fadeOut(tween(duration)) + shrinkVertically(tween(duration))) {
                            Text(destination.label, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelSmall, color = iconTint, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
