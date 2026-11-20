package com.hydro.reedance

import com.google.gson.JsonParser
import com.hydro.reedance.domain.grade
import com.hydro.reedance.domain.practiceOptionOrder
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class PracticeOptionOrderTest {
    @Test fun choicePositionsVaryButCorrectIdDoesNotChange() {
        val q = JsonParser.parseString("""{"type":"choice","correctId":"a","options":[{"id":"a"},{"id":"b"},{"id":"c"}]}""").asJsonObject
        val original = q.deepCopy()
        val random = Random(42)
        val orders = List(30) { practiceOptionOrder(q, random) }
        assertEquals(setOf(0, 1, 2), orders.map { it.indexOf("a") }.toSet())
        assertTrue(orders.all { it.size == 3 && it.toSet() == setOf("a", "b", "c") })
        assertTrue(grade(q, listOf("a")).correct)
        assertEquals(original, q)
    }

    @Test fun scrambledChunksDoNotAlreadyShowTheAnswer() {
        val q = JsonParser.parseString("""{"type":"order","chunks":[{"id":"a"},{"id":"b"},{"id":"c"}]}""").asJsonObject
        val canonical = listOf("a", "b", "c")
        val random = Random(42)
        repeat(30) {
            val displayed = practiceOptionOrder(q, random)
            assertEquals(canonical.toSet(), displayed.toSet())
            assertFalse(grade(q, displayed).correct)
        }
        assertTrue(grade(q, canonical).correct)
    }
}
