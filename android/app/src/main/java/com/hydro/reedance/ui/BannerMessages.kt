package com.hydro.reedance.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive

private data class BannerCopy(val eyebrowFa: String, val eyebrowEn: String, val titleFa: String, val titleEn: String, val bodyFa: String, val bodyEn: String)
private val bannerCopies = listOf(
    BannerCopy("یادگیری با ریتم تو", "YOUR RHYTHM. YOUR WORDS.",
        "هر آهنگ،\nیک قدم جلوتر.", "Every song.\nOne step further.",
        "گوش بده، کشف کن و زبان را زندگی کن.", "Listen closer. Discover more. Make the words yours."),
    BannerCopy("کلمه‌هایی که می‌مانند", "WORDS THAT STAY.",
        "یک آهنگ،\nچند کشف تازه.", "One song.\nNew discoveries.",
        "از دل ترانه، واژه‌ها و عبارت‌های تازه پیدا کن.", "Discover new words and expressions inside a song."),
    BannerCopy("کم، اما پیوسته", "A LITTLE, EVERY DAY.",
        "با قدم‌های کوچک،\nریتمت را بساز.", "Small steps.\nYour own rhythm.",
        "یک تمرین کوتاه یا مرور واژه‌ها، شروع خوبی برای امروز است.", "A short practice or word review is a lovely start to today.")
)
private const val BANNER_MESSAGE_MILLIS = 6_000

/** Isolated timer: filling the indicator does not recompose Home or its song grid. */
@Composable internal fun BannerMessages(explore: () -> Unit, animate: Boolean, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val paused = LocalBackgroundMotion.current.paused
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var index by rememberSaveable { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(animate, reduced, paused, lifecycle) {
        if(animate && !reduced && !paused) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(isActive) {
                val remaining = ((1f - progress.value) * BANNER_MESSAGE_MILLIS).toInt().coerceAtLeast(1)
                progress.animateTo(1f, tween(remaining, easing = LinearEasing))
                index = (index + 1) % bannerCopies.size
                progress.snapTo(0f)
            }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // A bounded text viewport keeps the action and progress inside the banner
        // even at large font scales, and permits reading longer translated text.
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            AnimatedContent(targetState = index, modifier = Modifier.fillMaxWidth(),
                transitionSpec = {
                    val transform = if(reduced) (fadeIn(tween(0)) togetherWith fadeOut(tween(0)))
                    else (slideInVertically(tween(450)) { it } + fadeIn(tween(450))) togetherWith
                        (slideOutVertically(tween(450)) { -it } + fadeOut(tween(450)))
                    transform using null // The surrounding clipped text viewport owns the size.
                }, label = "banner-message") { selected ->
                val copy = bannerCopies[selected]
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(tr(copy.eyebrowFa, copy.eyebrowEn), color = Color(0xFFCADFFF), style = MaterialTheme.typography.labelMedium)
                    Text(tr(copy.titleFa, copy.titleEn), color = Color.White, style = MaterialTheme.typography.headlineLarge)
                    Text(tr(copy.bodyFa, copy.bodyEn), color = Color(0xFFE1ECFF))
                }
            }
        }
        PressButton(explore, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Blue)) {
            Text(tr("شروع یادگیری", "Start learning"))
        }
        LinearProgressIndicator(progress = { progress.value }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            color = Color.White, trackColor = Color.White.copy(alpha = .22f))
    }
}
