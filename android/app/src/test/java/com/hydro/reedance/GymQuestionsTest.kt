package com.hydro.reedance

import com.google.gson.JsonObject
import com.hydro.reedance.domain.GymQuestion
import com.hydro.reedance.domain.ListeningClip
import com.hydro.reedance.domain.selectGymRound
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class GymQuestionsTest {
    private fun question(song: String, id: String, type: String = "choice", clip: ListeningClip? = null) = GymQuestion(
        JsonObject().apply { addProperty("id", id); addProperty("type", type) },
        JsonObject().apply { addProperty("songId", song) },
        listening = clip
    )

    @Test fun prefersAnUnansweredQuestionWithinATypeThenTheOldestAnsweredOne() {
        val latest = question("a", "1")
        val oldest = question("a", "2")
        val fresh = question("b", "1")
        val history = mapOf(latest.key to 100L, oldest.key to 25L)
        assertEquals(listOf(fresh), selectGymRound(listOf(latest, oldest, fresh), history, Random(1)))
        assertEquals(listOf(oldest), selectGymRound(listOf(latest, oldest), history, Random(1)))
    }

    @Test fun picksOneOfAllSixTypesAndDoesNotGroupThemInAFixedOrder() {
        val packaged = listOf("choice", "order", "match", "typing").mapIndexed { index, type ->
            question(if(index % 2 == 0) "a" else "b", type, type)
        }
        val original = ListeningClip(0, 1000, emptyMap())
        val persian = original.copy(originalForMeaning = "The line from the song.")
        val alternatives = List(100) { index ->
            question("b", "clip-$index", "order", if(index % 2 == 0) original else persian)
        }
        val history = packaged.associate { it.key to 100L }
        val orders = (0..19).map { seed ->
            val picked = selectGymRound(alternatives + packaged, history, Random(seed))
            assertEquals(6, picked.size)
            assertTrue(picked.containsAll(packaged))
            assertEquals(1, picked.count { it.listening == original })
            assertEquals(1, picked.count { it.listening == persian })
            picked.map { it.listening?.let { clip -> if(clip.originalForMeaning == null) "original" else "persian" } ?: it.key.questionId }
        }
        assertTrue(orders.toSet().size > 1)
    }

    @Test fun missingTypesLeaveAShorterRoundInsteadOfRepeatingAvailableTypes() {
        val choice = question("a", "1")
        val pool = listOf(choice, choice, question("a", "2"), question("b", "3", "match"))
        val picked = selectGymRound(pool, emptyMap(), Random(1))
        assertEquals(2, picked.size)
        assertEquals(2, picked.map { it.key }.toSet().size)
        assertTrue(picked.any { it.key.questionId == "3" })
    }
}
