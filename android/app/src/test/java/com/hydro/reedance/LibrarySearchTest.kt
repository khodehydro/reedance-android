package com.hydro.reedance

import com.google.gson.JsonObject
import com.hydro.reedance.data.Bookmark
import com.hydro.reedance.data.LibraryItemContent
import com.hydro.reedance.ui.LibraryOrder
import com.hydro.reedance.ui.LibraryRequest
import com.hydro.reedance.ui.prepareLibraryRows
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySearchTest {
    private val first = Bookmark("z-song", "sentences", "one", 10)
    private val second = Bookmark("a-song", "idioms", "two", 20)
    private val catalog = mapOf("z-song" to meta("Zebra"), "a-song" to meta("Álbum"))
    private fun meta(title: String) = JsonObject().apply { addProperty("title", title) }

    @Test fun searchFindsPersianTranslationAcrossTheCombinedTab() = runBlocking {
        val result = prepareLibraryRows(listOf(first, second), "phrases",
            LibraryRequest("tr", 3, "راه يك", "", LibraryOrder.NEWEST), catalog) { mark ->
            LibraryItemContent("", JsonObject().apply {
                if(mark == first) addProperty("translationFa", "یک راه دیگر")
            })
        }
        assertEquals(listOf(first), result)
    }

    @Test fun filteringAndSortingDoNotOpenPackagesForAnEmptyQuery() = runBlocking {
        val rows = listOf(first, second)
        val request = LibraryRequest("tr", 3, "", "", LibraryOrder.OLDEST)
        assertEquals(listOf(first, second), prepareLibraryRows(rows, "phrases", request, catalog) { error("Unexpected load") })
        assertEquals(listOf(second, first), prepareLibraryRows(rows, "phrases", request.copy(order = LibraryOrder.SOURCE), catalog) { error("Unexpected load") })
        assertEquals(listOf(first), prepareLibraryRows(rows, "phrases", request.copy(songId = first.songId), catalog) { error("Unexpected load") })
    }
}
