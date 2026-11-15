package com.hydro.reedance

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.hydro.reedance.data.Settings
import com.hydro.reedance.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReviewAnswerButtonsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun buttonsShareRowAndSizeAndKeepIndependentActions() {
        val answers = mutableListOf<Boolean>()
        compose.setContent {
            CompositionLocalProvider(LocalFa provides false, LocalReducedMotion provides true) {
                ReedanceTheme(Settings(language = "en")) { ReviewAnswerButtons(true) { answers.add(it) } }
            }
        }
        val remembered = compose.onNodeWithText("Remembered")
        val again = compose.onNodeWithText("Review again")
        val first = remembered.fetchSemanticsNode().boundsInRoot
        val second = again.fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top, 1f)
        assertEquals(first.height, second.height, 1f)
        assertEquals(first.width, second.width, 1f)
        assertTrue(first.right <= second.left)
        remembered.performClick()
        again.performClick()
        compose.runOnIdle { assertEquals(listOf(true, false), answers) }
    }
}
