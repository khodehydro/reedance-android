package com.hydro.reedance.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.media3.common.MediaItem
import java.io.File
import androidx.compose.runtime.key
import com.hydro.reedance.domain.CardClip
import com.hydro.reedance.domain.ClipUnit
import com.hydro.reedance.domain.groupCardClips

internal data class DialogueEvidence(val key: String, val audio: File, val startMs: Long, val endMs: Long)

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
    return DialogueEvidence("question:$questionIndex", audio, selected.minOf { checkNotNull(it.startMs) },
        selected.maxOf { checkNotNull(it.endMs) })
}

/** Installed ReeTalk turns share one track; invalid/unavailable turns break a group. */
internal fun dialogueCardClips(turns: List<DialogueTurn>, ids: List<String>, key: String): List<DialogueEvidence> {
    val linked = ids.toHashSet()
    val audio = turns.firstOrNull {
        val start = it.startMs ?: return@firstOrNull false
        val end = it.endMs ?: return@firstOrNull false
        it.id in linked && it.audio != null && start >= 0 && end > start
    }?.audio ?: return emptyList()
    val units = turns.map {
        if(it.audio == audio) ClipUnit(it.id, it.startMs, it.endMs) else ClipUnit(it.id, null, null)
    }
    return groupCardClips(units, linked).mapIndexed { index, clip ->
        DialogueEvidence("$key:$index", audio, clip.startMs, clip.endMs)
    }
}

/** Clip in the media timeline, never with a wall-clock timer or a padded seek. */
internal fun dialogueMediaItem(audio: File, startMs: Long?, endMs: Long?): MediaItem {
    val item = MediaItem.Builder().setUri(Uri.fromFile(audio))
    if(startMs != null && endMs != null) {
        item.setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startMs).setEndPositionMs(endMs).build())
    }
    return item.build()
}

@Composable internal fun EvidencePlayback(clip: DialogueEvidence?, onEnd: () -> Unit, onError: () -> Unit) {
    if(clip != null) key(clip.key) {
        ClippedPlayback(clip.audio, CardClip(clip.startMs, clip.endMs), onEnd = onEnd, onError = onError)
    }
}
