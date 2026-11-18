package com.hydro.reedance

import com.hydro.reedance.data.Bookmark
import com.hydro.reedance.ui.libraryBookmarks
import org.junit.Assert.*
import org.junit.Test

class LibraryBookmarksTest {
    @Test fun fiveThousandBookmarksRemainCorrectlyFilteredAndOrdered() {
        val rows = (0 until 5000).map { index ->
            Bookmark("song-${index % 10}", if(index % 2 == 0) "grammar" else "vocabulary", "item-$index", index.toLong())
        }.reversed()
        val notes = libraryBookmarks(rows, "grammar")
        assertEquals(2500, notes.size)
        assertEquals(4998L, notes.first().createdAt)
        assertEquals(0L, notes.last().createdAt)
        assertEquals(notes.size, notes.map { it.songId to it.itemId }.distinct().size)
    }

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
    @Test fun phrasesTabCombinesSavedSentencesAndExpressionsNewestFirst() {
        val expression = Bookmark("song-a", "idioms", "same", 10)
        val sentence = Bookmark("song-a", "sentences", "same", 30)
        val word = Bookmark("song-b", "vocabulary", "word", 40)
        assertEquals(listOf(sentence, expression), libraryBookmarks(listOf(expression, word, sentence), "phrases"))
    }
}
