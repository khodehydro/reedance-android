package com.hydro.reedance

import androidx.media3.common.Player
import com.hydro.reedance.media.OverlayLyric
import com.hydro.reedance.media.overlaySongForPlayback
import com.hydro.reedance.media.timedOverlayLyric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverlayLyricsTest {
    @Test fun followsPlaybackAndDoesNotShowStaleLyricsInExplicitGaps() {
        val first = OverlayLyric(1_000, 2_000, "first", "", "")
        val second = OverlayLyric(3_000, null, "second", "", "")
        val lines = listOf(first, second)
        assertNull(timedOverlayLyric(lines, 999))
        assertEquals(first, timedOverlayLyric(lines, 1_000))
        assertNull(timedOverlayLyric(lines, 2_000))
        assertEquals(second, timedOverlayLyric(lines, 3_000))
        assertEquals(first, timedOverlayLyric(lines, 1_500)) // Seek backwards.
        assertNull(timedOverlayLyric(emptyList(), 10_000))
    }
    @Test fun pauseKeepsTheSameSongButStoppingOrReplacingDismissesIt() {
        assertEquals("song", overlaySongForPlayback("song", null, true, Player.STATE_READY))
        assertEquals("song", overlaySongForPlayback("song", "song", false, Player.STATE_READY))
        assertNull(overlaySongForPlayback("song", "song", false, Player.STATE_ENDED))
        assertNull(overlaySongForPlayback("song", "song", false, Player.STATE_IDLE))
        assertNull(overlaySongForPlayback("next", "song", false, Player.STATE_READY))
    }
}
