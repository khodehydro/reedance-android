package com.hydro.reedance

import com.hydro.reedance.domain.HoneycombLetterBoard
import com.hydro.reedance.domain.GameWordDefinition
import com.hydro.reedance.domain.areHoneycombNeighbors
import com.hydro.reedance.domain.buildHoneycombBoard
import com.hydro.reedance.domain.gameWordKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
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
    @Test fun restoresTheDrawnPathEvenWhenItSharesAnotherWordsCells() {
        val board = HoneycombLetterBoard("ABABAB".toList(), 2, mapOf("first" to listOf(0, 1), "second" to listOf(2, 3)))
        assertEquals(listOf(2, 3), board.restoredSolvedPath("first", listOf(2, 3)))
        assertEquals(listOf(4, 5), board.restoredSolvedPath("first", listOf(4, 5)))
        assertEquals(listOf(0, 1), board.restoredSolvedPath("first", listOf(0, 5)))
    }
    @Test fun correctAlternateSpellingReturnsTheExactDrawnRoute() {
        val board = HoneycombLetterBoard("ABABAB".toList(), 2,
            mapOf("first" to listOf(0, 1), "second" to listOf(2, 3)))
        assertEquals(listOf(2, 3), board.acceptedSolvedPath("first", listOf(2, 3)))
        assertEquals(listOf(4, 5), board.acceptedSolvedPath("first", listOf(4, 5)))
        assertEquals(listOf(2, 3), board.acceptedSolvedPath("second", listOf(2, 3)))
        assertNull(board.acceptedSolvedPath("first", listOf(0, 5))) // not adjacent
        assertNull(board.acceptedSolvedPath("first", listOf(1, 0))) // wrong spelling
        assertNull(board.acceptedSolvedPath("first", listOf(0, 0))) // repeated cell
    }

    @Test fun seededFillersDoNotReuseAnswerLettersAndAreBalanced() {
        val targets = words.zip(listOf("Ankara", "masal", "zaman")).map { (word, answer) ->
            word.copy(targetLanguage = "tr", letterAnswer = answer)
        }
        val answers = targets.flatMap { it.letterAnswer.toList() }
        repeat(20) { seed ->
            val board = buildHoneycombBoard(targets.map { it.copy(songId = "${it.songId}-$seed") })
            val reserved = board.wordPaths.values.flatten().toSet()
            val fillers = board.letters.filterIndexed { index, _ -> index !in reserved }
            assertTrue(fillers.none { filler -> answers.any { it.equals(filler, ignoreCase = true) } })
            assertEquals(answers.count { it.equals('a', ignoreCase = true) },
                board.letters.count { it.equals('a', ignoreCase = true) })
            assertEquals(fillers.size, fillers.toSet().size)
        }
    }

    @Test fun seededBoardsDoNotPlaceWholeAnswersAlongTheFirstRow() {
        repeat(40) { seed ->
            val targets = words.map { it.copy(songId = "${it.songId}-$seed") }
            val board = buildHoneycombBoard(targets)
            val cells = board.wordPaths.values.flatten()
            assertEquals(cells.size, cells.distinct().size)
            board.wordPaths.values.forEach { path ->
                assertTrue(path.any { it >= board.columns })
                assertTrue(path.zipWithNext().all { (a, b) -> areHoneycombNeighbors(a, b, board.columns) })
            }
        }
    }
}
