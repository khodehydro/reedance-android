package com.hydro.reedance

import com.hydro.reedance.domain.HoneycombLetterBoard
import com.hydro.reedance.domain.GameWordDefinition
import com.hydro.reedance.domain.areHoneycombNeighbors
import com.hydro.reedance.domain.buildHoneycombBoard
import com.hydro.reedance.domain.gameWordKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class HoneycombBoardTest {
    private val words = listOf(
        GameWordDefinition("en", "song-a", "en:moon", "Song A", "moon", "moon", "ماه", "moon"),
        GameWordDefinition("en", "song-b", "en:light", "Song B", "light", "light", "نور", "light"),
        GameWordDefinition("en", "song-c", "en:hope", "Song C", "hope", "hope", "امید", "hope")
    )

    @Test fun everyAnswerIsPresentOnItsOwnConnectedPath() {
        val board = buildHoneycombBoard(words)
        val allPathCells = board.wordPaths.values.flatten()
        assertEquals(allPathCells.size, allPathCells.toSet().size)
        words.forEach { word ->
            val path = board.wordPaths.getValue(gameWordKey(word.songId, word.lexemeId))
            assertEquals(word.letterAnswer, path.map { board.letters[it] }.joinToString(""))
            assertTrue(path.zipWithNext().all { (a, b) -> areHoneycombNeighbors(a, b, board.columns) })
        }
        assertEquals(0, board.letters.size % board.columns)
        assertTrue(board.letters.size > allPathCells.size)
    }

    @Test fun boardLayoutIsStableAcrossCalls() {
        assertEquals(buildHoneycombBoard(words), buildHoneycombBoard(words))
    }
    @Test fun alternateRouteCannotConsumeAnotherWordsReservedCells() {
        val board = HoneycombLetterBoard("ABABAB".toList(), 2, mapOf("first" to listOf(0, 1), "second" to listOf(2, 3)))
        assertFalse(board.canUsePath("first", listOf(2, 3)))
        assertTrue(board.canUsePath("first", listOf(4, 5)))
        assertEquals(listOf(0, 1), board.restoredSolvedPath("first", listOf(2, 3)))
        assertEquals(listOf(4, 5), board.restoredSolvedPath("first", listOf(4, 5)))
        assertEquals(listOf(0, 1), board.restoredSolvedPath("first", listOf(0, 5)))
    }
}
