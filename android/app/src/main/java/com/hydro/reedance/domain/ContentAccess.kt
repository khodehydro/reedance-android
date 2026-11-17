package com.hydro.reedance.domain

import com.google.gson.JsonObject
import com.hydro.reedance.data.s
import java.time.Instant

/** Newest publication first; legacy undated entries retain server/feed order. */
fun latestSongs(songs: List<JsonObject>): List<JsonObject> = songs.sortedByDescending {
    runCatching { Instant.parse(it.s("publishedAt")).toEpochMilli() }.getOrDefault(Long.MIN_VALUE)
}.take(40)

/** Audio availability is deliberately independent of the educational preview. */
data class LessonAccess(val premium: Boolean, val subscribed: Boolean = false) {
    val preview: Boolean get() = premium && !subscribed
    val allowsBackgroundPlayback: Boolean get() = subscribed

    fun canBookmarkContent(): Boolean = !premium || subscribed
    fun canAddBookmark(sectionCount: Int): Boolean = subscribed || sectionCount < FREE_BOOKMARKS_PER_SECTION

    fun limit(section: String): Int = if(!preview) Int.MAX_VALUE else when(section) {
        "vocabulary" -> 5
        "lyrics" -> 3
        else -> 2
    }

    companion object {
        const val FREE_BOOKMARKS_PER_SECTION = 30
    }
}
