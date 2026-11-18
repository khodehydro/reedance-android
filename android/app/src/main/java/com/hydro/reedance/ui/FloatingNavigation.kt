package com.hydro.reedance.ui

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

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
    val usePlatformBackdropBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    if(usePlatformBackdropBlur) {
        // Keep Scaffold measurement stable; render the actual capsule in its own
        // translucent window so Android can blur the live app content behind it.
        Box(Modifier.fillMaxWidth().heightIn(min = slotHeight).padding(horizontal = inset))
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            val view = LocalView.current
            val window = (view.parent as? DialogWindowProvider)?.window
            val blurRadius = with(LocalDensity.current) { 28.dp.roundToPx() }
            DisposableEffect(window, blurRadius) {
                if(window != null) {
                    window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
                    val params = window.attributes
                    params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    params.width = WindowManager.LayoutParams.MATCH_PARENT
                    params.height = WindowManager.LayoutParams.WRAP_CONTENT
                    params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    if(window.windowManager.isCrossWindowBlurEnabled) {
                        params.dimAmount = .08f
                        params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND or WindowManager.LayoutParams.FLAG_DIM_BEHIND
                        params.blurBehindRadius = blurRadius
                    } else {
                        params.dimAmount = 0f
                        params.flags = params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv() and
                            WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
                        params.blurBehindRadius = 0
                    }
                    window.attributes = params
                }
                onDispose { }
            }
            Box(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = inset).padding(vertical = 8.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                FrostedNavigationCapsule(page, compact, destinations, duration, onNavigate)
            }
        }
    } else {
        // Older Android versions do not expose cross-window backdrop blur.
        Box(Modifier.fillMaxWidth().heightIn(min = slotHeight).padding(horizontal = inset), contentAlignment = Alignment.BottomCenter) {
            FrostedNavigationCapsule(page, compact, destinations, duration, onNavigate)
        }
    }
}

@Composable private fun FrostedNavigationCapsule(
    page: String,
    compact: Boolean,
    destinations: List<Destination>,
    duration: Int,
    onNavigate: (String) -> Unit
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val glassShape = RoundedCornerShape(32.dp)
    val primaryGlass = if(dark) Color(0xA61B2028) else Color(0xB8F7F8FA)
    val secondaryGlass = if(dark) Color(0x92262D38) else Color(0xA0E7EBF1)
    val foreground = if(dark) Color.White.copy(alpha = .96f) else Color(0xFF202630)
    val mutedForeground = if(dark) Color.White.copy(alpha = .70f) else Color(0xFF3D4652).copy(alpha = .72f)
    Box(
        Modifier.fillMaxWidth()
            .shadow(16.dp, glassShape, clip = false)
            .clip(glassShape)
            .background(Brush.linearGradient(listOf(primaryGlass, secondaryGlass, primaryGlass)))
            .border(1.dp, Brush.verticalGradient(listOf(
                Color.White.copy(alpha = if(dark) .38f else .82f),
                Color.White.copy(alpha = if(dark) .12f else .42f),
                foreground.copy(alpha = .10f)
            )), glassShape)
    ) {
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
            Color.White.copy(alpha = if(dark) .14f else .46f),
            Color.Transparent,
            foreground.copy(alpha = .04f)
        ))))
        Box(Modifier.fillMaxWidth().padding(horizontal = 26.dp).height(1.dp)
            .background(Color.White.copy(alpha = if(dark) .42f else .78f), CircleShape))
        Row(Modifier.fillMaxWidth().selectableGroup().padding(5.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            destinations.forEach { destination ->
                val active = page == destination.id
                val tint = if(active) foreground else mutedForeground
                Column(Modifier.weight(1f).clip(RoundedCornerShape(25.dp))
                    .background(if(active) foreground.copy(alpha = if(dark) .13f else .12f) else Color.Transparent)
                    .selectable(active, role = Role.Tab, onClick = { onNavigate(destination.id) })
                    .semantics { contentDescription = destination.label }
                    .heightIn(min = 48.dp).padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
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
