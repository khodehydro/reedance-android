package com.hydro.reedance

import com.hydro.reedance.ui.connectMatch
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchConnectionsTest {
    @Test fun buildsAndReassignsOneToOneConnections() {
        val first = connectMatch(emptyList(), 3, 0, "a")
        assertEquals(listOf("a", "", ""), first)
        val moved = connectMatch(first, 3, 2, "a")
        assertEquals(listOf("", "", "a"), moved)
        val next = connectMatch(moved, 3, 0, "b")
        assertEquals(listOf("b", "", "a"), next)
        assertEquals(next, connectMatch(next, 3, 0, "b"))
        assertEquals(listOf("c", "", "a"), connectMatch(next, 3, 0, "c"))
    }
}
