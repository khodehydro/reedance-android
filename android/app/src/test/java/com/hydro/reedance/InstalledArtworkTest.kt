package com.hydro.reedance

import com.hydro.reedance.ui.artworkSampleSize
import org.junit.Assert.assertEquals
import org.junit.Test

class InstalledArtworkTest {
    @Test fun largeCoverKeepsEnoughPixelsOnBothAxes() {
        assertEquals(1, artworkSampleSize(1200, 800, 960, 576))
        assertEquals(2, artworkSampleSize(2400, 1600, 960, 576))
        assertEquals(1, artworkSampleSize(1600, 700, 960, 512))
    }

    @Test fun thumbnailsAndOversizedDecodesStayBounded() {
        assertEquals(16, artworkSampleSize(1200, 1200, 64, 64))
        assertEquals(2, artworkSampleSize(4096, 4096, 2048, 2048))
        assertEquals(1, artworkSampleSize(-1, -1, 64, 64))
    }
}
