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
        var stories = 0
        compose.setContent {
            CompositionLocalProvider(LocalFa provides false, LocalReducedMotion provides true) {
                ReedanceTheme(Settings(language = "en")) {
                    LessonTabs(tab, { tab = it }, mode, { mode = it }, false, {}, { quizzes++ }, { stories++ })
                }
            }
        }
        compose.onNodeWithText("Lyrics: Original").performClick()
        compose.onNodeWithText("Lyrics: Line by line").assertExists()
        compose.onNodeWithText("Song story").performClick()
        compose.runOnIdle { assertEquals(1, stories) }
        compose.runOnIdle { assertEquals(1, mode) }
        compose.onNodeWithText("Song quiz").performClick()
        compose.runOnIdle { assertEquals(1, quizzes); assertEquals(0, tab) }
        compose.onNodeWithText("Phrases").performClick()
        compose.onNodeWithText("Lyrics: Line by line").assertDoesNotExist()
        compose.onNodeWithText("Lyrics").performClick()
        compose.onNodeWithText("Lyrics: Line by line").assertExists()
    }
    @Test fun autoScrollIsAvailableInCompactLyricsListAndHiddenInSingleView() {
        var single by mutableStateOf(false)
        var autoScroll by mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalFa provides false, LocalReducedMotion provides true) {
                ReedanceTheme(Settings(language = "en")) {
                    LessonTabs(0, {}, 0, {}, single, { single = !single }, {}, null,
                        compact = true, autoScroll = autoScroll, onAutoScroll = { autoScroll = it })
                }
            }
        }
        val toggle = compose.onNodeWithContentDescription("Auto-scroll lyrics")
        toggle.assertIsOn().performClick()
        toggle.assertIsOff()
        compose.onNodeWithContentDescription("Show single lyric").performClick()
        toggle.assertDoesNotExist()
        compose.onNodeWithContentDescription("Show lyrics list").performClick()
        toggle.assertIsOff()
    }
}
