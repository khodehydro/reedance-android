package com.hydro.reedance

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.hydro.reedance.data.Settings
import com.hydro.reedance.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LessonTabsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun modeSwitchesAndQuizUsesActionWhileOtherTabsHideModes() {
        var tab by mutableIntStateOf(0)
        var mode by mutableIntStateOf(0)
        var quizzes = 0
        compose.setContent {
            CompositionLocalProvider(LocalFa provides false, LocalReducedMotion provides true) {
                ReedanceTheme(Settings(language = "en")) {
                    LessonTabs(tab, { tab = it }, mode, { mode = it }, false, {}, { quizzes++ })
                }
            }
        }
        compose.onNodeWithText("Line by line").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(1, mode) }
        compose.onNodeWithText("Song quiz").performClick()
        compose.runOnIdle { assertEquals(1, quizzes); assertEquals(0, tab) }
        compose.onNodeWithText("Phrases").performClick()
        compose.onNodeWithText("Line by line").assertDoesNotExist()
        compose.onNodeWithText("Lyrics").performClick()
        compose.onNodeWithText("Line by line").assertIsSelected()
    }
}
