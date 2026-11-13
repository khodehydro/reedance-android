package com.hydro.reedance

import com.google.gson.JsonParser
import com.hydro.reedance.data.*
import com.hydro.reedance.domain.*
import org.junit.Assert.*
import org.junit.Test
class LearningTest {
    private val day = 86400000L
    @Test fun reviewIntervalsMatchWeb() {
        assertEquals(Schedule(2, 1000 + 2 * day), schedule(1, true, 1000))
        assertEquals(Schedule(3, 1000 + 4 * day), schedule(2, true, 1000))
        assertEquals(Schedule(4, 1000 + 8 * day), schedule(3, true, 1000))
        assertEquals(Schedule(5, 1000 + 16 * day), schedule(4, true, 1000))
        assertEquals(Schedule(5, 1000 + 16 * day), schedule(5, true, 1000))
        assertEquals(Schedule(1, 61000), schedule(5, false, 1000))
    }
    @Test fun unicodeAndAccentsArePreserved() {
        assertEquals("sí", normalizeAnswer("  SI\u0301! ", "es-ES"))
        assertNotEquals(normalizeAnswer("sí"), normalizeAnswer("si"))
        assertEquals("밤", normalizeAnswer(" 밤… ", "ko-KR"))
        assertEquals("hope", normalizeAnswer(" HOPE?! "))
    }
    private fun lesson() = JsonParser.parseString(javaClass.getResource("/lesson.json")!!.readText()).asJsonObject
    @Test fun allFourQuestionTypesHaveCorrectAndIncorrectCases() {
        val questions = lesson().a("questions")
        for(q in questions) {
            val answer = when(q.s("type")) {
                "choice" -> listOf(q.s("correctId"))
                "typing" -> listOf(q.strings("accepted").first())
                "order" -> q.a("chunks").map { it.s("id") }
                else -> q.a("pairs").map { it.s("id") }
            }
            assertTrue(complete(q, answer)); assertTrue(grade(q, answer).correct)
            assertFalse(grade(q, listOf("wrong")).correct)
            assertFalse(complete(q, emptyList()))
        }
    }
    @Test fun matchOnlyQueuesIncorrectPairs() {
        val q = lesson().a("questions").first { it.s("type") == "match" }
        val pairs = q.a("pairs")
        val result = grade(q, listOf(pairs[0].s("id"), "wrong"))
        assertFalse(result.correct)
        assertEquals(setOf(pairs[1].s("lexemeId")), result.missed)
    }
}
