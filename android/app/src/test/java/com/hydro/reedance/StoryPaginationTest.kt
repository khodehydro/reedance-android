package com.hydro.reedance

import com.google.gson.JsonParser
import com.hydro.reedance.ui.lessonStory
import com.hydro.reedance.ui.storyPageStarts
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test

class StoryPaginationTest {
    @Test fun onlyTheRequestedLessonCanSupplyItsStory() {
        val lesson = JsonParser.parseString("""{"songId":"gulum-2","locale":"tr-TR"}""").asJsonObject
        assertNull(lessonStory(lesson, "gulum-2"))
        lesson.add("story", JsonParser.parseString("""{"title":"A story","text":"A short story."}"""))
        assertNull(lessonStory(lesson, "other-song"))
        assertEquals("A story", lessonStory(lesson, "gulum-2")?.title)
    }

    @Test fun largerTextOrTranslationsMoveLinesForwardAndSmallerTextMovesThemBack() {
        val normal = listOf(30, 30, 30, 30, 30, 30)
        val expanded = listOf(45, 45, 45, 45, 45, 45)
        assertEquals(listOf(0, 3), storyPageStarts(normal, 100, 5))
        assertEquals(listOf(0, 2, 4), storyPageStarts(expanded, 100, 5))
        assertEquals(listOf(0, 3), storyPageStarts(normal, 100, 5))
    }

    @Test fun oversizedLineGetsItsOwnScrollablePageWithoutDroppingTheFollowingLine() {
        assertEquals(listOf(0, 1, 2), storyPageStarts(listOf(40, 180, 40), 100, 5))
        assertEquals(listOf(0), storyPageStarts(emptyList(), 100, 5))
    }
}
