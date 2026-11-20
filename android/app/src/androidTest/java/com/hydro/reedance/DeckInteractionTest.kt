package com.hydro.reedance

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.hydro.reedance.ui.Deck
import com.hydro.reedance.ui.LocalReducedMotion
import com.hydro.reedance.ui.PressButton
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DeckInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dragAcrossOutgoingSheetMovesTheParentBeforeAnimationEnds() {
        lateinit var scroll: ScrollState
        var current by mutableIntStateOf(0)
        compose.setContent {
            scroll = rememberScrollState()
            MaterialTheme {
                CompositionLocalProvider(LocalReducedMotion provides false) {
                    Column(Modifier.fillMaxWidth().height(420.dp).testTag("practice-scroll").verticalScroll(scroll)) {
                        val item = current
                        Deck(item) {
                            if(item == 0) Box(Modifier.fillMaxWidth().height(350.dp))
                            else PressButton({}) { Text("New card") }
                        }
                        Spacer(Modifier.height(1500.dp))
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { current = 1 }
            compose.mainClock.advanceTimeBy(48)
            compose.onNodeWithText("New card").assertIsEnabled()
            compose.onNodeWithTag("practice-scroll").performTouchInput {
                swipe(Offset(centerX, height * .39f), Offset(centerX, height * .1f), durationMillis = 100)
            }
            compose.runOnIdle { assertTrue("Drag must move the list during the card transition", scroll.value > 0) }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }
}
