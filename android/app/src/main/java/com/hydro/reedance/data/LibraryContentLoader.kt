package com.hydro.reedance.data

import com.google.gson.JsonObject
import com.hydro.reedance.domain.foreignLyricIds
import com.hydro.reedance.domain.teachingVocabulary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class LibraryItemContent(val title: String, val item: JsonObject?, val lesson: JsonObject? = null)

/** Bounded per-package index. Concurrent visible cards share a single read/index build. */
internal class LibraryContentLoader(private val dao: LearningDao, private val content: LocalContentRepository) {
    private data class Indexed(val revision: String, val title: String, val lesson: JsonObject, val items: Map<Pair<String, String>, JsonObject>)
    private val lock = Mutex()
    private val cache = object : LinkedHashMap<String, Indexed>(4, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Indexed>?) = size > 4
    }
    private fun Indexed.resolve(mark: Bookmark) = LibraryItemContent(title, items[mark.itemType to mark.itemId], lesson)
    fun cached(mark: Bookmark): LibraryItemContent? = synchronized(cache) { cache[mark.songId]?.resolve(mark) }

    suspend fun load(mark: Bookmark): LibraryItemContent? = lock.withLock {
        val row = dao.installed(mark.songId)
        if(row == null || row.state !in listOf("VERIFIED", "BUNDLED", "DIRECT_TEST", "LOCAL_PASSWORD")) {
            synchronized(cache) { cache.remove(mark.songId) }
            return@withLock null
        }
        val revision = "${row.activeVersionCode}:${row.signedPayloadSha256}:${row.activePath}"
        val cached = synchronized(cache) { cache[mark.songId] }
        if(cached?.revision == revision) return@withLock cached.resolve(mark)
        val lesson = content.installedLesson(mark.songId) ?: return@withLock null
        val indexed = withContext(Dispatchers.Default) {
            val items = buildMap<Pair<String, String>, JsonObject> {
                val foreign = lesson.foreignLyricIds()
                for(type in listOf("vocabulary", "grammar", "idioms", "sentences")) {
                    val cards = if(type == "vocabulary") lesson.teachingVocabulary()
                        else lesson.a(type).filterNot { it.s("lineId") in foreign }
                    cards.forEach { item -> put(type to item.s(if(type == "vocabulary") "lexemeId" else "id"), item) }
                }
            }
            Indexed(revision, lesson.s("title"), lesson, items)
        }
        synchronized(cache) { cache[mark.songId] = indexed }
        indexed.resolve(mark)
    }
}
