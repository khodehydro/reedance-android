package com.hydro.reedance.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics

@Composable internal fun AnimatedBookmarkIcon(saved: Boolean, description: String? = null,
    tint: Color = if(saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) {
    val duration = if(LocalReducedMotion.current) 0 else 180
    AnimatedContent(saved, transitionSpec = {
        (fadeIn(tween(duration)) + scaleIn(tween(duration), initialScale = .65f)) togetherWith
            (fadeOut(tween(duration)) + scaleOut(tween(duration), targetScale = .8f))
    }, label = "bookmark-fill") { selected ->
        AppIcon(if(selected) UiIcons.BookmarkFilled else UiIcons.Bookmark, description, tint = tint)
    }
}

@Composable internal fun BookmarkButton(saved: Boolean, onClick: () -> Unit) {
    val state = if(saved) tr("نشان‌شده", "Saved") else tr("نشان نشده", "Not saved")
    PressIconButton(onClick, Modifier.semantics { selected = saved; stateDescription = state }) {
        AnimatedBookmarkIcon(saved, tr("نشان کردن آهنگ", "Bookmark song"))
    }
}
