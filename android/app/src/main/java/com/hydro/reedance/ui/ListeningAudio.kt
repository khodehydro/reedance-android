package com.hydro.reedance.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.google.gson.JsonObject
import com.hydro.reedance.data.*
import com.hydro.reedance.domain.*
import java.io.File
import kotlinx.coroutines.CancellationException

/** Main-thread ownership shared by all foreground previews, not the MediaSession. */
internal object ClipPlaybackOwner {
    private var stopCurrent: (() -> Unit)? = null
    fun claim(stop: () -> Unit) { stopCurrent?.invoke(); stopCurrent = stop }
    fun release(stop: () -> Unit) { if(stopCurrent === stop) stopCurrent = null }
}

@Composable internal fun ListeningAudio(model: AppModel, entry: GymQuestion, access: LessonAccess, onUnlock: () -> Unit) {
    val clip = entry.listening ?: return
    val visible = !access.preview || entry.source.a("lyrics").take(access.limit("lyrics")).any { it.n("startMs", -1) == clip.startMs }
    val clips = if(visible) listOf(CardClip(clip.startMs, clip.endMs)) else emptyList()
    SongClipButton(model, entry.source, SongCardClips(clips, !visible), onUnlock,
        label = tr("پخش تکهٔ آهنگ", "Play song clip"))
}

@Composable internal fun SongCardAudio(model: AppModel, source: JsonObject, card: JsonObject, section: String,
    access: LessonAccess, onUnlock: (() -> Unit)? = null, enabled: Boolean = true) {
    if(!enabled) return
    val clips = remember(source, card, section, access) {
        val ids = when(section) {
            "vocabulary" -> songWordLineIds(source, card)
            "grammar" -> card.strings("lineIds")
            else -> listOf(card.s("lineId")).filter(String::isNotBlank)
        }
        songCardClips(source, ids, access)
    }
    SongClipButton(model, source, clips, onUnlock)
}

@Composable private fun SongClipButton(model: AppModel, source: JsonObject, clips: SongCardClips,
    onUnlock: (() -> Unit)? = null, label: String = tr("شنیدن در آهنگ", "Hear it in the song")) {
    if(clips.playable.isEmpty() && !(clips.locked && onUnlock != null)) return
    val installed by model.installedIds.collectAsState()
    val songId = source.s("songId")
    val file by produceState<Pair<JsonObject, File>?>(null, source, songId in installed) {
        value = null
        if(songId in installed) value = try { source to model.listeningAudio(source) }
        catch(cancelled: CancellationException) { throw cancelled } catch(_: Exception) { null }
    }
    val audio = file?.takeIf { it.first === source && songId in installed }?.second ?: return
    key(source, audio, clips) {
        ForegroundClipButtons(audio, clips.playable, label, onUnlock = onUnlock.takeIf { clips.locked }, beforePlay = model::pause)
    }
}

@Composable private fun ForegroundClipButtons(audio: File, clips: List<CardClip>, label: String,
    onUnlock: (() -> Unit)?, beforePlay: () -> Unit) {
    val active = LocalPageActive.current && LocalDeckInteractive.current
    var selected by remember(active) { mutableIntStateOf(-1) }
    var failed by remember(active) { mutableStateOf(false) }
    val clip = clips.getOrNull(selected)
    if(active && clip != null) key(selected) {
        ClippedPlayback(audio, clip, music = true, beforePlay = beforePlay,
            onEnd = { selected = -1 }, onError = { failed = true; selected = -1 })
    }
    ClipButtons(clips.size, selected, label, active) { index ->
        if(clips.isEmpty()) onUnlock?.invoke()
        else { failed = false; selected = if(selected == index) -1 else index }
    }
    if(failed) ClipPlaybackError()
}

/** A single, foreground-only player; the shared song session is never sought. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable internal fun ClippedPlayback(audio: File, clip: CardClip, music: Boolean = false,
    beforePlay: () -> Unit = {}, onEnd: () -> Unit, onError: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val pauseSong = rememberUpdatedState(beforePlay)
    val ended = rememberUpdatedState(onEnd)
    val failed = rememberUpdatedState(onError)
    DisposableEffect(context, lifecycle, audio, clip, music) {
        var player: ExoPlayer? = null
        val release = { player?.release(); player = null }
        val stop: () -> Unit = { release(); ended.value() }
        val observer = LifecycleEventObserver { _, event ->
            if(event == Lifecycle.Event.ON_PAUSE) {
                ClipPlaybackOwner.release(stop)
                stop()
            }
        }
        lifecycle.addObserver(observer)
        if(!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) ended.value()
        else try {
            ClipPlaybackOwner.claim(stop)
            pauseSong.value()
            val preview = ExoPlayer.Builder(context).build().also { player = it }
            if(music) preview.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            preview.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) { if(state == Player.STATE_ENDED) ended.value() }
                override fun onPlayerError(error: PlaybackException) { failed.value() }
            })
            preview.setMediaItem(dialogueMediaItem(audio, clip.startMs, clip.endMs))
            preview.prepare()
            preview.play()
        } catch(_: Exception) {
            ClipPlaybackOwner.release(stop)
            release()
            failed.value()
        }
        onDispose {
            lifecycle.removeObserver(observer)
            ClipPlaybackOwner.release(stop)
            release()
        }
    }
}

@Composable internal fun ClipButtons(count: Int, selected: Int, label: String, enabled: Boolean = true, select: (Int) -> Unit) {
    PressOutlinedButton({ select(if(selected >= 0) selected else 0) }, modifier = Modifier.heightIn(min = 48.dp), enabled = enabled, shape = InsetControlShape) {
        AppIcon(if(selected >= 0) UiIcons.PauseFilled else UiIcons.PlayFilled, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(if(selected >= 0) tr("توقف صدا", "Stop audio") else label)
    }
    if(count > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for(index in 0 until minOf(count, 8)) {
            FilterChip(selected = index == selected, enabled = enabled, onClick = { select(index) },
                label = { Text(tr("${index + 1}".map { "۰۱۲۳۴۵۶۷۸۹"[it - '0'] }.joinToString(""), "${index + 1}")) })
        }
    }
}

@Composable internal fun ClipPlaybackError() {
    Text(tr("پخش این تکه ممکن نیست. دوباره امتحان کن.", "Could not play this clip. Try again."), color = MaterialTheme.colorScheme.error)
}

@Composable internal fun ClipSubscriptionInfo(model: AppModel, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss,
        title = { Text(tr("اشتراک پریمیوم", "Premium subscription")) },
        text = { Text(tr("خرید اشتراک هنوز به فروشگاه متصل نشده است؛ در این نسخه پرداختی انجام نمی‌شود.",
            "Subscription checkout is not connected yet. No payment is taken in this version.")) },
        confirmButton = { PressTextButton({ dismiss(); model.premiumRequired.tryEmit(Unit) }) { Text(tr("دیدن امکانات پریمیوم", "Explore Premium")) } },
        dismissButton = { PressTextButton(dismiss) { Text(tr("بعداً", "Later")) } })
}
