package com.hydro.reedance

import com.hydro.reedance.domain.ReminderPolicy as Policy
import org.junit.Assert.*
import org.junit.Test

class ReminderPolicyTest {
    private val visit = 1_000_000L
    @Test fun realMilestonesAndWeeklyContinuation() {
        listOf(12L, 24L, 36L, 72L, 168L, 336L).forEachIndexed { stage, hours ->
            assertEquals(stage.toLong(), Policy.stage(hours * 3600000L))
            assertEquals(stage.toLong() - 1, Policy.stage(hours * 3600000L - 1))
        }
    }
    @Test fun firstReminderWaitsTwelveHoursInEveryBuild() {
        assertFalse(Policy.eligible(visit + Policy.UNIT_MILLIS - 1L, visit, 0L))
        assertTrue(Policy.eligible(visit + Policy.UNIT_MILLIS, visit, 0L))
        assertEquals(visit + 2 * Policy.UNIT_MILLIS, Policy.nextAt(visit, visit + Policy.UNIT_MILLIS))
    }
    @Test fun sameStageCannotNotifyTwiceAndDelayedWorkSkipsCatchupSpam() {
        val hour = 3600000L
        assertFalse(Policy.eligible(visit + 23 * hour, visit, visit + 12 * hour))
        assertTrue(Policy.eligible(visit + 72 * hour, visit, visit + 12 * hour))
        assertEquals(visit + 168 * hour, Policy.nextAt(visit, visit + 72 * hour))
    }
    @Test fun returnResetsAndInvalidClocksDoNotNotify() {
        assertFalse(Policy.eligible(visit + 1000L, visit, visit - 50000L))
        assertFalse(Policy.eligible(Long.MAX_VALUE, 0L, 0L))
        assertFalse(Policy.eligible(visit, visit + 1, 0L))
        assertFalse(Policy.eligible(visit + Policy.UNIT_MILLIS, visit, Long.MAX_VALUE))
    }
    @Test fun messagesVaryByStageAndUseActualStats() {
        assertEquals(5, (0L..4L).map { Policy.message(it, 0, 0, true) }.toSet().size)
        assertTrue(Policy.message(0, 7, 0, false).contains("7 words"))
        assertTrue(Policy.message(1, 0, 3, false).contains("Starter"))
    }
}
