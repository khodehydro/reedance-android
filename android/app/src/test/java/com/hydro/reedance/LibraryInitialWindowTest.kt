package com.hydro.reedance

import com.hydro.reedance.data.Bookmark
import com.hydro.reedance.ui.libraryInitialWindow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LibraryInitialWindowTest {
    @Test fun largeLibraryOnlyResolvesFirstFourRowsIncludingUnavailableResults() = runBlocking {
        val rows = (0 until 5000).map { Bookmark("song-$it", "grammar", "item-$it", it.toLong()) }
        val loaded = mutableListOf<Bookmark>()
        val result = libraryInitialWindow(rows) { mark -> loaded.add(mark); null }
        assertEquals(rows.take(4), loaded)
        assertEquals(rows.take(4).toSet(), result.keys)
        assertTrue(result.values.all { it == null })
    }
    @Test fun songListDoesNotReadLessonPackages() = runBlocking {
        val rows = listOf(Bookmark("song", "song", "song", 1))
        val result = libraryInitialWindow(rows) { error("Song cards need catalog metadata only") }
        assertTrue(result.isEmpty())
    }
    @Test fun unavailablePackageDoesNotBlockOtherInitialRows() = runBlocking {
        val rows = (0..3).map { Bookmark("song-$it", "grammar", "item-$it", it.toLong()) }
        var calls = 0
        val result = libraryInitialWindow(rows) { calls++; error("Unavailable") }
        assertEquals(4, calls)
        assertEquals(rows.toSet(), result.keys)
    }
    @Test(expected = CancellationException::class)
    fun switchingTabCancelsInitialWindowLoad() { runBlocking {
        libraryInitialWindow(listOf(Bookmark("song", "grammar", "item", 1))) {
            throw CancellationException("Tab changed")
        }
    } }
}
