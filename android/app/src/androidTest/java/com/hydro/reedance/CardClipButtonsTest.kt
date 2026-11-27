package com.hydro.reedance

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.hydro.reedance.ui.ClipButtons
import com.hydro.reedance.ui.LocalFa
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CardClipButtonsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun multiClipShowsFirstPlayAndAtMostEightChoices() {
        val selected = mutableListOf<Int>()
        compose.setContent {
            CompositionLocalProvider(LocalFa provides false) {
                Column { ClipButtons(10, -1, "Hear clip") { selected.add(it) } }
            }
        }
        compose.onNodeWithText("Hear clip").performClick()
        compose.onNodeWithText("8").performScrollTo().performClick()
        compose.onNodeWithText("9").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(0, 7), selected) }
    }
}
