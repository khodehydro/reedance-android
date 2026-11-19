package com.hydro.reedance

import com.google.gson.JsonObject
import com.hydro.reedance.ui.exploreArtists
import org.junit.Assert.assertEquals
import org.junit.Test

class ExploreArtistsTest {
    @Test fun groupsCatalogSongsByNormalizedArtistName() {
        val first = song("one", "Ayaz Erdoğan")
        val second = song("two", " ayaz   erdoğan ")
        val other = song("three", "Zara")
        val artists = exploreArtists(listOf(other, first, second, song("unknown", " ")))
        assertEquals(listOf("Ayaz Erdoğan", "Zara"), artists.map { it.name })
        assertEquals(listOf(first, second), artists.first().songs)
        assertEquals(listOf(other), artists.last().songs)
    }

    private fun song(id: String, artist: String) = JsonObject().apply {
        addProperty("id", id)
        addProperty("artist", artist)
    }
}
