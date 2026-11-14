package com.hydro.reedance

import com.hydro.reedance.domain.ReminderPolicy
import org.junit.Assert.*
import org.junit.Test

class ReminderPolicyTest {
    private val start = 1_000_000L
    @Test fun waitsTwelveHoursSinceVisit() {
        assertFalse(ReminderPolicy.eligible(start + ReminderPolicy.INTERVAL - 1, start, 0L))
        assertTrue(ReminderPolicy.eligible(start + ReminderPolicy.INTERVAL, start, 0L))
    }
    @Test fun enforcesCooldownSinceLastNotification() {
        val now = start + 3 * ReminderPolicy.INTERVAL
        assertFalse(ReminderPolicy.eligible(now, start, now - 1000L))
        assertTrue(ReminderPolicy.eligible(now, start, now - ReminderPolicy.INTERVAL))
    }
    @Test fun neverRemindsBeforeFirstVisit() {
        assertFalse(ReminderPolicy.eligible(Long.MAX_VALUE, 0L, 0L))
    }
    @Test fun clockGoingBackwardsDoesNotSpam() {
        assertFalse(ReminderPolicy.eligible(start, start + 1, 0L))
        assertFalse(ReminderPolicy.eligible(start + ReminderPolicy.INTERVAL, start, Long.MAX_VALUE))
    }
}
