package com.hydro.reedance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.hydro.reedance.ui.LibraryScrollConnection
import org.junit.Assert.*
import org.junit.Test

class LibraryScrollConnectionTest {
    @Test fun shortListDragDoesNotCollapseTabs() {
        val changes = mutableListOf<Boolean>()
        val connection = LibraryScrollConnection(40f) { changes.add(it) }
        repeat(10) {
            assertEquals(Offset.Zero, connection.onPostScroll(Offset.Zero, Offset(0f, -100f), NestedScrollSource.UserInput))
        }
        assertTrue(changes.isEmpty())
    }
    @Test fun actualScrollCrossesThresholdAndReverseScrollExpands() {
        val changes = mutableListOf<Boolean>()
        val connection = LibraryScrollConnection(40f) { changes.add(it) }
        connection.onPostScroll(Offset(0f, -25f), Offset.Zero, NestedScrollSource.UserInput)
        assertTrue(changes.isEmpty())
        connection.onPostScroll(Offset(0f, -20f), Offset.Zero, NestedScrollSource.UserInput)
        assertEquals(listOf(true), changes)
        connection.onPostScroll(Offset(0f, 45f), Offset.Zero, NestedScrollSource.UserInput)
        assertEquals(listOf(true, false), changes)
    }
    @Test fun overscrollClearsPartialDistanceAndResetExpandsAfterItemsRemoved() {
        val changes = mutableListOf<Boolean>()
        val connection = LibraryScrollConnection(40f) { changes.add(it) }
        connection.onPostScroll(Offset(0f, -25f), Offset.Zero, NestedScrollSource.UserInput)
        connection.onPostScroll(Offset.Zero, Offset(0f, -200f), NestedScrollSource.UserInput)
        connection.onPostScroll(Offset(0f, -25f), Offset.Zero, NestedScrollSource.UserInput)
        assertTrue(changes.isEmpty())
        connection.reset()
        assertEquals(listOf(false), changes)
    }
}
