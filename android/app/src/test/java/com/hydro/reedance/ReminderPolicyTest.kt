package com.hydro.reedance

import com.hydro.reedance.domain.ReminderPolicy as Policy
import org.junit.Assert.*
import org.junit.Test

class ReminderPolicyTest {
    private val visit = 1_000_000L
    @Test fun realMilestonesAndWeeklyContinuation() {
        listOf(12L, 24L, 36L, 72L, 168L, 336L).forEachIndexed { stage, hours ->
            assertEquals(stage.toLong(), Policy.stage(hours * 3600000L, false))
            assertEquals(stage.toLong() - 1, Policy.stage(hours * 3600000L - 1, false))
        }
    }
    @Test fun temporaryDebugFirstStageIsTenSeconds() {
        assertFalse(Policy.eligible(visit + 9999L, visit, 0L, true))
        assertTrue(Policy.eligible(visit + 10000L, visit, 0L, true))
        assertEquals(visit + 20000L, Policy.nextAt(visit, visit + 10000L, true))
    }
    @Test fun sameStageCannotNotifyTwiceAndDelayedWorkSkipsCatchupSpam() {
        assertFalse(Policy.eligible(visit + 19000L, visit, visit + 10000L, true))
        assertTrue(Policy.eligible(visit + 60000L, visit, visit + 10000L, true))
        assertEquals(visit + 140000L, Policy.nextAt(visit, visit + 60000L, true))
    }
    @Test fun returnResetsAndInvalidClocksDoNotNotify() {
        assertFalse(Policy.eligible(visit + 1000L, visit, visit - 50000L, true))
        assertFalse(Policy.eligible(Long.MAX_VALUE, 0L, 0L, true))
        assertFalse(Policy.eligible(visit, visit + 1, 0L, true))
        assertFalse(Policy.eligible(visit + 10000L, visit, Long.MAX_VALUE, true))
    }
    @Test fun messagesVaryByStageAndUseActualStats() {
        assertEquals(5, (0L..4L).map { Policy.message(it, 0, 0, true) }.toSet().size)
        assertTrue(Policy.message(0, 7, 0, false).contains("7 words"))
        assertTrue(Policy.message(1, 0, 3, false).contains("Starter"))
    }
}
