package com.hydro.reedance

import com.hydro.reedance.ui.storyPageStarts
import org.junit.Assert.assertEquals
import org.junit.Test

class StoryPaginationTest {
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
