package com.hydro.reedance

import com.google.gson.JsonObject
import com.hydro.reedance.domain.GymQuestion
import com.hydro.reedance.domain.ListeningClip
import com.hydro.reedance.domain.selectGymRound
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class GymQuestionsTest {
    private fun question(song: String, id: String, type: String = "choice", listening: Boolean = false) = GymQuestion(
        JsonObject().apply { addProperty("id", id); addProperty("type", type) },
        JsonObject().apply { addProperty("songId", song) },
        listening = if (listening) ListeningClip(0, 1000, emptyMap()) else null
    )

    @Test fun unansweredQuestionsComeBeforeOldestAnsweredQuestions() {
        val latest = question("a", "1")
        val oldest = question("a", "2")
        val fresh = question("b", "1")
        val picked = selectGymRound(listOf(latest, fresh, oldest), mapOf(latest.key to 100L, oldest.key to 25L), 2, Random(1))
        assertEquals(setOf(fresh, oldest), picked.toSet())
    }

    @Test fun freshQuestionsAlternateSongsAndNeverRepeatAKeyInOneRound() {
        val a1 = question("a", "1")
        val b1 = question("b", "1")
        val pool = listOf(a1, a1, b1, question("a", "2"), question("b", "2"))
        val picked = selectGymRound(pool, emptyMap(), 5, Random(2))
        assertEquals(4, picked.size)
        assertEquals(4, picked.map { it.key }.toSet().size)
        assertTrue(picked.zipWithNext().all { (a, b) -> a.key.songId != b.key.songId })
    }

    @Test fun listeningCannotCrowdOutOtherQuestionTypesEvenWithABiggerFreshPool() {
        val formats = listOf("choice", "order", "match", "typing")
        val packaged = formats.map { question("a", it, type = it) }
        val listening = List(100) { question("b", "clip-$it", type = "order", listening = true) }
        val history = packaged.associate { it.key to 100L }
        val orders = (0..19).map { seed ->
            val picked = selectGymRound(listening + packaged, history, 5, Random(seed))
            assertTrue(picked.containsAll(packaged))
            assertEquals(1, picked.count { it.listening != null })
            picked.indexOfFirst { it.listening != null }
        }
        assertTrue(orders.toSet().size > 1)
    }

    @Test fun listeningIsCappedWhenEnoughPackagedQuestionsExistAndFillsEmptySlotsOtherwise() {
        val listening = List(8) { question("a", "clip-$it", type = "order", listening = true) }
        val packaged = List(4) { question("a", "choice-$it") }
        assertEquals(2, selectGymRound(listening + packaged, emptyMap(), 5, Random(1)).count { it.listening != null })
        assertEquals(4, selectGymRound(listening + packaged.take(1), emptyMap(), 5, Random(1)).count { it.listening != null })
    }

}
