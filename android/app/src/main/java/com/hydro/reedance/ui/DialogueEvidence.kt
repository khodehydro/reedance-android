package com.hydro.reedance.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

internal data class DialogueEvidence(val questionIndex: Int, val audio: File, val startMs: Long, val endMs: Long)

/** Match Studio: play continuously from the earliest evidence start to the latest end. */
internal fun dialogueEvidence(turns: List<DialogueTurn>, ids: List<String>, questionIndex: Int): DialogueEvidence? {
    if(ids.isEmpty()) return null
    val selected = turns.filter { it.id in ids }
    if(selected.size != ids.toSet().size) return null
    val audio = selected.firstOrNull()?.audio ?: return null
    if(selected.any { t ->
        val start = t.startMs
        val end = t.endMs
        t.audio != audio || start == null || end == null || start < 0 || end <= start
    }) return null
    return DialogueEvidence(questionIndex, audio, selected.minOf { checkNotNull(it.startMs) },
        selected.maxOf { checkNotNull(it.endMs) })
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable internal fun EvidencePlayback(clip: DialogueEvidence?, onEnd: () -> Unit, onError: () -> Unit) {
    val context = LocalContext.current
    val ended = rememberUpdatedState(onEnd)
    val failed = rememberUpdatedState(onError)
    DisposableEffect(clip, context) {
        if(clip == null) onDispose { }
        else {
            val player = ExoPlayer.Builder(context).build()
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) { if(state == Player.STATE_ENDED) ended.value() }
                override fun onPlayerError(error: PlaybackException) { failed.value() }
            }
            player.addListener(listener)
            player.setMediaItem(MediaItem.Builder().setUri(Uri.fromFile(clip.audio))
                .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.startMs).setEndPositionMs(clip.endMs).build()).build())
            player.playWhenReady = true
            player.prepare()
            onDispose { player.removeListener(listener); player.release() }
        }
    }
}
