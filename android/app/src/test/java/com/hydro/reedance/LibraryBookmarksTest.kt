package com.hydro.reedance

import com.hydro.reedance.data.Bookmark
import com.hydro.reedance.ui.libraryBookmarks
import org.junit.Assert.*
import org.junit.Test

class LibraryBookmarksTest {
    @Test fun eachTabShowsOnlyItsTypeNewestFirstAcrossSongs() {
        val old = Bookmark("song-a", "vocabulary", "same", 10)
        val recent = Bookmark("song-b", "vocabulary", "same", 30)
        val song = Bookmark("song-a", "song", "song-a", 40)
        val note = Bookmark("song-a", "grammar", "note", 20)
        val all = listOf(old, song, note, recent)
        assertEquals(listOf(recent, old), libraryBookmarks(all, "vocabulary"))
        assertEquals(listOf(song), libraryBookmarks(all, "song"))
        assertEquals(listOf(note), libraryBookmarks(all, "grammar"))
        assertTrue(libraryBookmarks(all, "sentences").isEmpty())
        assertEquals(listOf(old), libraryBookmarks(all - recent, "vocabulary"))
    }
}
