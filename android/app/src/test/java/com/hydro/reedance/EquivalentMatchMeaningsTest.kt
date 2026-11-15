package com.hydro.reedance

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.hydro.reedance.domain.*
import org.junit.Assert.*
import org.junit.Test

class EquivalentMatchMeaningsTest {
    private fun question(): JsonObject = JsonParser.parseString("""{
        "type":"match","pairs":[
            {"id":"kalp","text":"kalp","en":"heart","fa":"قلب","lexemeId":"v1"},
            {"id":"gonul","text":"gönül","en":"heart","fa":"دل","lexemeId":"v2"},
            {"id":"ev","text":"ev","en":"house","fa":"خانه","lexemeId":"v3"}
        ]}
    """).asJsonObject

    @Test fun duplicateMeaningsAcceptEitherBoxWithoutMissedWords() {
        val q = question()
        val swapped = listOf("gonul", "kalp", "ev")
        assertTrue(grade(q, swapped, "tr-TR", "en").correct)
        assertTrue(grade(q, swapped, "tr-TR", "en").missed.isEmpty())
        assertEquals(listOf(true, true, true), matchCorrectness(q, swapped, "en"))
    }

    @Test fun onlyVisibleTranslationDeterminesEquivalence() {
        val swapped = listOf("gonul", "kalp", "ev")
        val result = grade(question(), swapped, "tr-TR", "fa")
        assertFalse(result.correct)
        assertEquals(setOf("v1", "v2"), result.missed)
    }

    @Test fun unrelatedMeaningsStillFailAndOnlyWrongRowsAreMissed() {
        val result = grade(question(), listOf("gonul", "ev", "kalp"))
        assertFalse(result.correct)
        assertEquals(setOf("v2", "v3"), result.missed)
        assertEquals(listOf(true, false, false), matchCorrectness(question(), listOf("gonul", "ev", "kalp")))
    }

    @Test fun normalizedWhitespaceAndCaseAreEquivalent() {
        val q = question()
        q.getAsJsonArray("pairs")[1].asJsonObject.addProperty("en", "  HEART\u00a0 ")
        assertTrue(grade(q, listOf("gonul", "kalp", "ev")).correct)
    }

    @Test fun missingMeaningsUnknownIdsAndReusedBoxesAreNotEquivalent() {
        val q = question()
        assertFalse(grade(q, listOf("kalp", "kalp", "ev")).correct)
        assertFalse(grade(q, listOf("unknown", "kalp", "ev")).correct)
        assertFalse(grade(q, listOf("gonul", "kalp")).correct)
        q.getAsJsonArray("pairs")[0].asJsonObject.addProperty("en", "")
        q.getAsJsonArray("pairs")[1].asJsonObject.addProperty("en", "")
        assertFalse(grade(q, listOf("gonul", "kalp", "ev")).correct)
        assertTrue(grade(q, listOf("kalp", "gonul", "ev")).correct)
    }

    @Test fun equivalenceSupportsMoreThanTwoWordsAndCanonicalUnicode() {
        val q = question()
        q.getAsJsonArray("pairs").forEach { it.asJsonObject.addProperty("en", "café") }
        q.getAsJsonArray("pairs")[1].asJsonObject.addProperty("en", "cafe\u0301")
        assertTrue(grade(q, listOf("ev", "kalp", "gonul")).correct)
        q.getAsJsonArray("pairs")[2].asJsonObject.addProperty("en", "cafe")
        assertFalse(grade(q, listOf("ev", "kalp", "gonul")).correct)
    }
}
