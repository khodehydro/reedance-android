package com.hydro.reedance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** Square invitation card; shared liquid shader with a distinct orchid/coral palette. */
@Composable internal fun ShareAppDialog(onDismiss: () -> Unit, onShare: () -> Unit) {
    val backgroundMotion = LocalBackgroundMotion.current
    DisposableEffect(backgroundMotion) {
        backgroundMotion.overlays++
        onDispose { backgroundMotion.overlays-- }
    }
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp)).background(Color(0xFF180923))) {
            // Only this foreground artwork animates while the underlying page is paused.
            CompositionLocalProvider(LocalBackgroundMotion provides remember { BackgroundMotion() }) {
                BannerGradient(true, Modifier.matchParentSize(), FluidPalette.Aurora)
            }
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0x33180923), Color(0xDD180923)))))
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).background(Color.White.copy(alpha = .14f), CircleShape), contentAlignment = Alignment.Center) {
                    AppIcon(UiIcons.ShareFilled, null, Modifier.size(24.dp), tint = Color(0xFFFFD4C6))
                }
                Text(tr("موسیقی، با هم قشنگ‌تره", "Better with friends"), color = Color.White,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(tr("ری‌دنس را به دوستت معرفی کن؛ یک آهنگ، شروع یک زبان تازه.", "Send Reedance to a friend. One song could be the start of a new language."),
                    color = Color(0xFFF4DAEF), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                PressButton(onShare, Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD4C6), contentColor = Color(0xFF351039))) {
                    AppIcon(UiIcons.ShareFilled, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("اشتراک‌گذاری برنامه", "Share Reedance"))
                }
                PressTextButton(onDismiss, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(tr("فعلاً نه", "Not now"), color = Color.White)
                }
            }
        }
    }
}
