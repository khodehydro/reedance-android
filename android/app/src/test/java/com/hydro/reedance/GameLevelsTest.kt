package com.hydro.reedance

import com.hydro.reedance.domain.balancedGameLevelSizes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameLevelsTest {
    @Test fun holdsFewerThanThreeWordsUntilAPlayableRoundExists() {
        assertEquals(emptyList<Int>(), balancedGameLevelSizes(0))
        assertEquals(emptyList<Int>(), balancedGameLevelSizes(1))
        assertEquals(emptyList<Int>(), balancedGameLevelSizes(2))
    }

    @Test fun everyRoundHasThreeToFiveWordsAndKeepsAllWords() {
        for(total in 3..100) {
            val levels = balancedGameLevelSizes(total)
            assertEquals(total, levels.sum())
            assertTrue(levels.all { it in 3..5 })
        }
    }

    @Test fun balancesLeftoverWordsInsteadOfCreatingOneOrTwoWordRounds() {
        assertEquals(listOf(3, 3), balancedGameLevelSizes(6))
        assertEquals(listOf(4, 3), balancedGameLevelSizes(7))
        assertEquals(listOf(4, 4, 3), balancedGameLevelSizes(11))
    }
}
