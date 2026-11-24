package com.hydro.reedance

import androidx.compose.ui.unit.LayoutDirection
import com.hydro.reedance.ui.mainTabAfterSwipe
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
}
