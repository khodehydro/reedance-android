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

internal fun matchesLibraryTab(row: Bookmark, type: String): Boolean =
    if(type == "phrases") row.itemType == "idioms" || row.itemType == "sentences" else row.itemType == type

internal fun libraryBookmarks(rows: List<Bookmark>, type: String): List<Bookmark> =
    rows.filter { matchesLibraryTab(it, type) }.sortedByDescending { it.createdAt }

/** Resolve only visible bookmark cards from their own source package, never the currently playing song. */
@Composable internal fun LibraryBookmarkCard(model: AppModel, mark: Bookmark, catalogSong: JsonObject?, openSong: (String) -> Unit,
    preloaded: Boolean = false, preloadedContent: LibraryItemContent? = null) {
    val initial = remember(mark, preloaded, preloadedContent) { if(preloaded) preloadedContent else model.cachedLibraryItem(mark) }
    var loading by remember(mark, preloaded) { mutableStateOf(!preloaded && initial == null && mark.itemType != "song") }
    val data by produceState<LibraryItemContent?>(initial, mark, preloaded, preloadedContent) {
        if(preloaded) {
            value = preloadedContent
            loading = false
            return@produceState
        }
        loading = value == null && mark.itemType != "song"
        try {
            value = if(mark.itemType == "song") null else model.libraryItem(mark)
        } catch(cancelled: CancellationException) { throw cancelled } catch(_: Exception) { value = null } finally { loading = false }
    }
    val item = data?.item
    val source = catalogSong?.s("title") ?: data?.title ?: mark.songId
    val remove: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if(mark.itemType != "song") Text(source, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Spacer(Modifier.weight(1f))
            ItemBookmarkButton(true) { model.removeBookmark(mark) }
        }
    }
    if(mark.itemType == "grammar" && item != null) {
        LearningNote(item, fitTablesWhenPossible = true, save = remove)
    } else Panel {
        when {
            mark.itemType == "song" -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { Foreign(source, large = true) }
                    PressOutlinedButton({ openSong(mark.songId) }, shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppIcon(UiIcons.PlayFilled, tr("باز کردن آهنگ", "Open song"), Modifier.size(18.dp))
                            Text(tr("باز کردن", "Open"))
                        }
                    }
                }
                catalogSong?.s("artist")?.takeIf { it.isNotBlank() }?.let { Foreign(it) }
            }
            loading -> {
                Column(Modifier.fillMaxWidth().heightIn(min = 160.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(Modifier.fillMaxWidth(.6f).height(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {}
                    Surface(Modifier.fillMaxWidth().height(72.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) {}
                }
            }
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
