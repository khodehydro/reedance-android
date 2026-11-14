package com.hydro.reedance

import com.hydro.reedance.domain.PlaybackCheckpoint
import org.junit.Assert.*
import org.junit.Test

class PlaybackCheckpointTest {
    @Test fun firstAndPeriodicCheckpointsAreBoundedToFiveSeconds() {
        val policy = PlaybackCheckpoint()
        assertTrue(policy.shouldSave(1000, 1000, false))
        assertFalse(policy.shouldSave(3000, 3000, false))
        assertTrue(policy.shouldSave(6000, 6000, false))
    }
    @Test fun pauseAndSeekCanSaveBeforePeriodicDeadline() {
        val policy = PlaybackCheckpoint()
        assertTrue(policy.shouldSave(1000, 1000, false))
        assertTrue(policy.shouldSave(2500, 2000, true))
        assertTrue(policy.shouldSave(19000, 2100, true))
    }
    @Test fun pausedOrRepeatedCallbacksDoNotWriteIdenticalPositions() {
        val policy = PlaybackCheckpoint()
        assertTrue(policy.shouldSave(5000, 1000, true))
        assertFalse(policy.shouldSave(5000, 2000, true))
        assertFalse(policy.shouldSave(5200, 7000, false))
        assertTrue(policy.shouldSave(5600, 8000, false))
    }
    @Test fun rewindingIsNotMistakenForAnUnchangedPosition() {
        val policy = PlaybackCheckpoint()
        assertTrue(policy.shouldSave(19000, 1000, true))
        assertTrue(policy.shouldSave(2000, 1100, true))
    }
}
