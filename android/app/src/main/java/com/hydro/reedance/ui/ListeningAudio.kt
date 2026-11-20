package com.hydro.reedance.ui

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.hydro.reedance.data.AppModel
import com.hydro.reedance.domain.GymQuestion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation

/** A foreground-only clipped player; never seeks the shared song session. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable internal fun ListeningAudio(model: AppModel, entry: GymQuestion) {
    val clip = entry.listening ?: return
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val active = LocalPageActive.current
    var request by remember(entry.key, active) { mutableIntStateOf(0) }
    var playing by remember(entry.key, active) { mutableStateOf(false) }
    var failed by remember(entry.key, active) { mutableStateOf(false) }
    DisposableEffect(lifecycle, entry.key, active) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) { request = 0; playing = false }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(entry.key, request, active) {
        if (!active || request == 0) return@LaunchedEffect
        var player: ExoPlayer? = null
        try {
            val file = model.listeningAudio(entry.source)
            model.pause()
            val preview = ExoPlayer.Builder(context).build()
            player = preview
            preview.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            preview.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) { playing = false; request = 0 }
                }
                override fun onPlayerError(error: PlaybackException) {
                    failed = true; playing = false; request = 0
                }
            })
            val clipping = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.startMs).setEndPositionMs(clip.endMs).build()
            preview.setMediaItem(MediaItem.Builder().setUri(Uri.fromFile(file)).setClippingConfiguration(clipping).build())
            preview.prepare()
            preview.play()
            awaitCancellation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true; playing = false; request = 0
        } finally {
            player?.release()
        }
    }
    PressOutlinedButton({
        if (playing) { request = 0; playing = false }
        else { failed = false; playing = true; request = 1 }
    }, enabled = active) {
        AppIcon(if (playing) UiIcons.Pause else UiIcons.Play, null)
        Text(if (playing) tr("توقف صدا", "Stop audio") else tr("پخش تکهٔ آهنگ", "Play song clip"))
    }
    if (failed) Text(tr("پخش این تکه ممکن نیست. دوباره امتحان کن.", "Could not play this clip. Try again."), color = MaterialTheme.colorScheme.error)
}
