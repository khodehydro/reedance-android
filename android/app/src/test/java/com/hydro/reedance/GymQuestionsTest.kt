package com.hydro.reedance

import com.google.gson.JsonObject
import com.hydro.reedance.domain.GymQuestion
import com.hydro.reedance.domain.selectGymRound
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class GymQuestionsTest {
    private fun question(song: String, id: String) = GymQuestion(
        JsonObject().apply { addProperty("id", id) },
        JsonObject().apply { addProperty("songId", song) }
    )

    @Test fun unansweredQuestionsComeBeforeOldestAnsweredQuestions() {
        val latest = question("a", "1")
        val oldest = question("a", "2")
        val fresh = question("b", "1")
        val picked = selectGymRound(listOf(latest, fresh, oldest), mapOf(latest.key to 100L, oldest.key to 25L), 2, Random(1))
        assertEquals(listOf(fresh, oldest), picked)
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
}
