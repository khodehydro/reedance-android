package com.hydro.reedance

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.hydro.reedance.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** Pixel evidence, not merely shader compilation. Run on API26–32 and API33+. */
class MiniGradientMotionTest {
    @get:Rule val compose = createComposeRule()

    private fun changedFraction(a: ImageBitmap, b: ImageBitmap): Float {
        val first = a.toPixelMap(); val second = b.toPixelMap()
        var changed = 0; var samples = 0
        for(y in 0 until a.height step 3) for(x in 0 until a.width step 3) {
            val p = first[x, y]; val q = second[x, y]
            if(abs(p.red - q.red) + abs(p.green - q.green) + abs(p.blue - q.blue) > .04f) changed++
            samples++
        }
        return changed.toFloat() / samples.coerceAtLeast(1)
    }

    @Test fun playingChangesPixelsButPauseAndReducedMotionFreezeThem() {
        var playing by mutableStateOf(true)
        var reduced by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotion provides reduced) {
                BannerGradient(playing, Modifier.size(320.dp, 48.dp).testTag("mini-gradient"), FluidPalette.Midnight)
            }
        }
        compose.mainClock.advanceTimeBy(200)
        val before = compose.onNodeWithTag("mini-gradient").captureToImage()
        compose.mainClock.advanceTimeBy(3000)
        val after = compose.onNodeWithTag("mini-gradient").captureToImage()
        assertTrue("Playing gradient must visibly evolve within 3 seconds", changedFraction(before, after) > .05f)

        compose.runOnIdle { playing = false }
        compose.mainClock.advanceTimeByFrame()
        val paused = compose.onNodeWithTag("mini-gradient").captureToImage()
        compose.mainClock.advanceTimeBy(3000)
        assertEquals(0f, changedFraction(paused, compose.onNodeWithTag("mini-gradient").captureToImage()), 0f)

        compose.runOnIdle { reduced = true; playing = true }
        compose.mainClock.advanceTimeBy(200)
        val still = compose.onNodeWithTag("mini-gradient").captureToImage()
        compose.mainClock.advanceTimeBy(3000)
        assertEquals(0f, changedFraction(still, compose.onNodeWithTag("mini-gradient").captureToImage()), 0f)
    }
}
