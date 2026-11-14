package com.hydro.reedance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.hydro.reedance.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class WaveSeekBarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun accessibleSeekingUsesNativeSlider() {
        var position by mutableFloatStateOf(25f)
        compose.setContent {
            CompositionLocalProvider(LocalFa provides false, LocalReducedMotion provides true) {
                Box(Modifier.width(300.dp)) {
                    WaveSeekBar(position, 100f, false, { position = it }, {})
                }
            }
        }
        compose.onNodeWithContentDescription("Playback position").performSemanticsAction(SemanticsActions.SetProgress) { it(75f) }
        compose.runOnIdle { assertEquals(75f, position, .1f) }
    }

    @Test fun onlyPlayedTrackMoves() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalFa provides false, LocalReducedMotion provides false) {
                Box(Modifier.width(300.dp).background(Color.Black).testTag("wave")) {
                    WaveSeekBar(50f, 100f, true, {}, {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(200)
        val first = compose.onNodeWithTag("wave").captureToImage().toPixelMap()
        compose.mainClock.advanceTimeBy(500)
        val second = compose.onNodeWithTag("wave").captureToImage().toPixelMap()
        var playedChanges = 0
        var remainingChanges = 0
        for(y in 0 until first.height) for(x in 0 until first.width) {
            val a = first[x, y]; val b = second[x, y]
            if(abs(a.red - b.red) > .05f) {
                if(x < first.width * .45f) playedChanges++
                if(x > first.width * .6f) remainingChanges++
            }
        }
        assertTrue("Played wave should animate", playedChanges > 0)
        assertEquals("Unplayed track should stay straight and still", 0, remainingChanges)
    }
}
