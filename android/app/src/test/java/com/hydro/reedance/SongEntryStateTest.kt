package com.hydro.reedance

import com.hydro.reedance.domain.*
import org.junit.Assert.assertEquals
import org.junit.Test

class SongEntryStateTest {
    @Test fun noDownloadScreenDuringUnresolvedRead() {
        assertEquals(SongEntryState.LOADING, songEntryState(false, true, false))
        assertEquals(SongEntryState.INSTALLED, songEntryState(true, false, false))
    }
    @Test fun warmInstalledSongRendersImmediatelyDuringRevalidation() {
        assertEquals(SongEntryState.INSTALLED, songEntryState(true, true, false))
    }
    @Test fun downloadOnlyAfterConfirmedMissingAndErrorsNeverMeanMissing() {
        assertEquals(SongEntryState.NOT_INSTALLED, songEntryState(false, false, false))
        assertEquals(SongEntryState.ERROR, songEntryState(false, false, true))
        assertEquals(SongEntryState.ERROR, songEntryState(true, false, true))
    }
}
