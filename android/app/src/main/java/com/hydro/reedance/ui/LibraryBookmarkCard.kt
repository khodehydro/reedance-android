package com.hydro.reedance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import com.hydro.reedance.data.*
import kotlinx.coroutines.CancellationException

internal fun libraryBookmarks(rows: List<Bookmark>, type: String): List<Bookmark> =
    rows.filter { it.itemType == type }.sortedByDescending { it.createdAt }

/** Resolve only visible bookmark cards from their own source package, never the currently playing song. */
@Composable internal fun LibraryBookmarkCard(model: AppModel, mark: Bookmark, catalogSong: JsonObject?, openSong: (String) -> Unit) {
    var loading by remember(mark.songId) { mutableStateOf(true) }
    val data by produceState<JsonObject?>(null, mark.songId) {
        loading = true
        try {
            value = if(mark.itemType == "song") null else model.lessonForReview(mark.songId)
        } catch(cancelled: CancellationException) { throw cancelled } catch(_: Exception) { value = null } finally { loading = false }
    }
    val lesson = data?.takeIf { it.s("songId") == mark.songId }
    val item = lesson?.a(mark.itemType)?.find { it.s(if(mark.itemType == "vocabulary") "lexemeId" else "id") == mark.itemId }
    val source = catalogSong?.s("title") ?: lesson?.s("title") ?: mark.songId
    val remove: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(source, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ItemBookmarkButton(true) { model.removeBookmark(mark) }
        }
    }
    if(mark.itemType == "grammar" && item != null) {
        LearningNote(item, save = remove)
    } else Panel {
        when {
            mark.itemType == "song" -> {
                Foreign(source, large = true)
                catalogSong?.s("artist")?.takeIf { it.isNotBlank() }?.let { Foreign(it) }
                Action(tr("باز کردن آهنگ", "Open song"), onClick = { openSong(mark.songId) })
            }
            loading -> { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item == null -> {
                Text(mark.itemId)
                PersianText("برای دیدن این مورد، بستهٔ آهنگ را نصب کن. نشان و پیشرفتت حفظ شده‌اند.")
            }
            mark.itemType == "vocabulary" -> WordLearningContent(item)
            mark.itemType in listOf("idioms", "sentences") -> PhraseLearningContent(item)
        }
        remove()
    }
}
