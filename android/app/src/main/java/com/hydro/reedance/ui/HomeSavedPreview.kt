package com.hydro.reedance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.JsonObject
import com.hydro.reedance.data.*
import kotlinx.coroutines.CancellationException

private const val PreviewLimit = 2
private val previewTabs = listOf("song", "vocabulary", "grammar", "phrases")

internal fun recentHomeBookmarks(bookmarks: List<Bookmark>): Map<String, List<Bookmark>> =
    previewTabs.associateWith { type -> libraryBookmarks(bookmarks, type).take(PreviewLimit) }

@Composable internal fun HomeSavedPreview(model: AppModel, catalog: List<JsonObject>, openSong: (String) -> Unit,
    openLibrary: () -> Unit) {
    val bookmarks by model.bookmarks.collectAsStateWithLifecycle()
    if(bookmarks.isEmpty()) return
    val groups = remember(bookmarks) { recentHomeBookmarks(bookmarks) }
    val byId = remember(catalog) { catalog.associateBy { it.s("id") } }
    val labels = listOf(
        tr("آهنگ‌ها", "Songs") to UiIcons.Lyrics,
        tr("لغت‌ها", "Words") to UiIcons.Words,
        tr("نکته‌ها", "Notes") to UiIcons.Notes,
        tr("جمله‌ها و اصطلاحات", "Sentences & expressions") to UiIcons.Phrases
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("تازه‌های کتابخانه", "Recently saved"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            PressTextButton(openLibrary) { Text(tr("مشاهدهٔ همه", "View all")) }
        }
        previewTabs.forEachIndexed { index, type ->
            val entries = groups[type].orEmpty()
            if(entries.isNotEmpty()) Panel {
                val (label, icon) = labels[index]
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppIcon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(label, style = MaterialTheme.typography.titleMedium)
                }
                entries.forEach { mark ->
                    key(mark.songId, mark.itemType, mark.itemId) {
                        HomeSavedRow(model, mark, byId[mark.songId], if(type == "song") ({ openSong(mark.songId) }) else openLibrary)
                    }
                }
            }
        }
    }
}

@Composable private fun HomeSavedRow(model: AppModel, mark: Bookmark, song: JsonObject?, onOpen: () -> Unit) {
    val loaded by produceState(model.cachedLibraryItem(mark), mark) {
        if(mark.itemType != "song" && value == null) {
            try { value = model.libraryItem(mark) }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { /* The library still provides access to the saved bookmark. */ }
        }
    }
    val title = song?.s("title").orEmpty().ifBlank { loaded?.title.orEmpty().ifBlank { mark.songId } }
    val data = loaded?.item
    val main = when(mark.itemType) {
        "song" -> title
        "vocabulary" -> data?.s("lemma").orEmpty()
        "grammar" -> data?.tr("title", LearningLanguage.persian).orEmpty()
        else -> data?.s("text").orEmpty()
    }
    val subtitle = if(mark.itemType == "song") song?.s("artist").orEmpty() else title
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpen).heightIn(min = 48.dp).padding(vertical = 4.dp),
        verticalArrangement = Arrangement.Center) {
        Text(main.ifBlank { tr("مورد ذخیره‌شده", "Saved item") }, style = MaterialTheme.typography.bodyMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if(subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
