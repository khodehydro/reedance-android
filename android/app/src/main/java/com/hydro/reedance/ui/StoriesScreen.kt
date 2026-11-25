package com.hydro.reedance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydro.reedance.data.AppModel
import com.hydro.reedance.data.s
import com.hydro.reedance.domain.LessonAccess
import com.hydro.reedance.domain.PremiumEntitlement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class StoryEntry(val songId: String, val title: String, val songTitle: String)

@Composable internal fun StoriesScreen(model: AppModel, open: (String) -> Unit) {
    val installed by model.installedIds.collectAsStateWithLifecycle()
    val catalog by model.catalog.collectAsStateWithLifecycle()
    val subscribed by PremiumEntitlement.active.collectAsStateWithLifecycle()
    val visible = LocalPageActive.current
    val stories by produceState<List<StoryEntry>?>(null, installed, catalog, subscribed, visible) {
        if(!visible) return@produceState
        val catalogById = catalog.associateBy { it.s("id") }
        value = null
        value = withContext(Dispatchers.Default) {
            installed.mapNotNull { id ->
                val song = catalogById[id] ?: return@mapNotNull null
                if(LessonAccess(premium = song.s("access") == "paid", subscribed = subscribed).preview) return@mapNotNull null
                val lesson = try { model.lessonForReview(id) }
                    catch(cancelled: CancellationException) { throw cancelled }
                    catch(_: Exception) { null } ?: return@mapNotNull null
                val story = lessonStory(lesson, id) ?: return@mapNotNull null
                StoryEntry(id, story.title, song.s("title"))
            }.sortedWith(compareBy<StoryEntry> { it.title }.thenBy { it.songId })
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp,
        top = 20.dp, bottom = 20.dp + LocalOverlayBottomInset.current), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val entries = stories
        when {
            entries == null -> item { CircularProgressIndicator(Modifier.size(24.dp)) }
            entries.isEmpty() -> item { Panel {
                Text(tr("هنوز داستانی در دسترس نیست", "No stories available yet"), style = MaterialTheme.typography.titleMedium)
                Text(tr("داستان‌های آهنگ‌های دانلودشده اینجا نمایش داده می‌شوند.", "Stories from your downloaded songs appear here."))
            } }
            else -> items(entries, key = { it.songId }) { entry ->
                Surface(shape = GroupedCardShape) {
                    SettingsAction(UiIcons.HeadphonesFilled, entry.title, onClick = { open(entry.songId) })
                }
                if(entry.songTitle.isNotBlank()) Text(entry.songTitle, Modifier.padding(start = 16.dp, top = 4.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
