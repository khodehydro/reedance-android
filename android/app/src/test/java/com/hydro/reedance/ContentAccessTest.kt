package com.hydro.reedance

import com.google.gson.JsonObject
import com.hydro.reedance.domain.LessonAccess
import com.hydro.reedance.domain.latestSongs
import org.junit.Assert.*
import org.junit.Test

class ContentAccessTest {
    @Test fun premiumPreviewHasSeparateSectionLimits() {
        val access = LessonAccess(premium = true)
        assertEquals(5, access.limit("vocabulary"))
        assertEquals(3, access.limit("lyrics"))
        listOf("grammar", "idioms", "sentences").forEach { assertEquals(2, access.limit(it)) }
    }
    @Test fun freeAndSubscribedLessonsHaveNoPreviewCap() {
        assertFalse(LessonAccess(false).preview)
        assertFalse(LessonAccess(true, subscribed = true).preview)
        assertEquals(Int.MAX_VALUE, LessonAccess(false).limit("lyrics"))
        assertEquals(Int.MAX_VALUE, LessonAccess(true, true).limit("vocabulary"))
    }
    @Test fun freeBookmarkAndBackgroundLimitsLiftOnlyForPremium() {
        val free = LessonAccess(premium = false)
        val paidTrackOnFreePlan = LessonAccess(premium = true)
        val subscribed = LessonAccess(premium = true, subscribed = true)
        assertTrue(free.canBookmarkContent())
        assertFalse(paidTrackOnFreePlan.canBookmarkContent())
        assertTrue(subscribed.canBookmarkContent())
        assertTrue(free.canAddBookmark(29))
        assertFalse(free.canAddBookmark(30))
        assertTrue(subscribed.canAddBookmark(500))
        assertFalse(free.allowsBackgroundPlayback)
        assertTrue(subscribed.allowsBackgroundPlayback)
    }
    @Test fun homeCapsAtFortyNewestPublications() {
        val songs = (1..45).map { day -> JsonObject().apply {
            addProperty("id", "$day")
            addProperty("publishedAt", java.time.Instant.ofEpochSecond(day.toLong()).toString())
        } }
        val result = latestSongs(songs)
        assertEquals(40, result.size)
        assertEquals("45", result.first().get("id").asString)
        assertEquals("6", result.last().get("id").asString)
    }
    @Test fun missingDatesPreserveFeedOrderWithoutInventingChronology() {
        val songs = listOf("first", "second").map { JsonObject().apply { addProperty("id", it) } }
        assertEquals(songs, latestSongs(songs))
    }
}
