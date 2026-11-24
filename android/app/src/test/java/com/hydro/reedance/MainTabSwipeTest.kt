package com.hydro.reedance

import androidx.compose.ui.unit.LayoutDirection
import com.hydro.reedance.ui.mainTabAfterSwipe
import com.hydro.reedance.ui.explorePageForMainSwipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainTabSwipeTest {
    @Test fun swipesMoveOneTabAndDoNotWrap() {
        assertEquals("explore", mainTabAfterSwipe("home", true, LayoutDirection.Ltr))
        assertEquals("study", mainTabAfterSwipe("explore", true, LayoutDirection.Ltr))
        assertEquals("explore", mainTabAfterSwipe("study", false, LayoutDirection.Ltr))
        assertNull(mainTabAfterSwipe("study", true, LayoutDirection.Ltr))
        assertNull(mainTabAfterSwipe("home", false, LayoutDirection.Ltr))
        assertNull(mainTabAfterSwipe("library", true, LayoutDirection.Ltr))
    }

    @Test fun persianLayoutMirrorsSwipeDirection() {
        assertEquals("explore", mainTabAfterSwipe("home", false, LayoutDirection.Rtl))
        assertEquals("home", mainTabAfterSwipe("explore", true, LayoutDirection.Rtl))
    }
    @Test fun exploreOnlyExitsAfterReachingTheMatchingEdge() {
        assertEquals(1, explorePageForMainSwipe("explore", "study", 0))
        assertNull(explorePageForMainSwipe("explore", "study", 1))
        assertEquals(0, explorePageForMainSwipe("explore", "home", 1))
        assertNull(explorePageForMainSwipe("explore", "home", 0))
        assertEquals(0, explorePageForMainSwipe("home", "explore", 1))
        assertEquals(1, explorePageForMainSwipe("study", "explore", 0))
    }
}
