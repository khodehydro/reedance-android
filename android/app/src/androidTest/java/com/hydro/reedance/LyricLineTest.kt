package com.hydro.reedance

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import com.google.gson.JsonParser
import com.hydro.reedance.data.Settings
import com.hydro.reedance.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LyricLineTest {
    @get:Rule val compose = createComposeRule()
    private val line = JsonParser.parseString("""{"id":"line-one","startMs":4000,"text":"Merhaba dünya","translationEn":"Hello world","translationFa":"سلام دنیا","tokens":[{"surface":"Merhaba","translationEn":"Hello","translationFa":"سلام"},{"surface":"dünya","translationEn":"world","translationFa":"دنیا"}]}""").asJsonObject

    @Test fun lineShowsPlayActionAndTranslationWithoutTimestamp() {
        var plays = 0
        compose.setContent {
            CompositionLocalProvider(LocalFa provides false, LocalReducedMotion provides true) {
                ReedanceTheme(Settings(language = "en")) {
                    LyricLine(line, 1, false, { plays++ }, {})
                }
            }
        }
        compose.onNodeWithText("0:04").assertDoesNotExist()
        compose.onNodeWithText("Hello world").assertIsDisplayed()
        compose.onNodeWithContentDescription("Play line: Merhaba dünya").performClick()
        compose.runOnIdle { assertEquals(1, plays) }
    }

    @Test fun playingLineSwitchesToPauseAndBack() {
        var playing by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalFa provides false, LocalReducedMotion provides true) {
                ReedanceTheme(Settings(language = "en")) {
                    LyricLine(line, 1, true, { playing = !playing }, {}, playing = playing)
                }
            }
        }
        compose.onNodeWithContentDescription("Play line: Merhaba dünya").performClick()
        compose.onNodeWithContentDescription("Pause line: Merhaba dünya").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Play line: Merhaba dünya").assertIsDisplayed()
    }

    @Test fun singleLineHasNoPlaybackButtonButKeepsTextAndTranslation() {
        compose.setContent {
            CompositionLocalProvider(LocalFa provides false, LocalReducedMotion provides true) {
                ReedanceTheme(Settings(language = "en")) {
                    LyricLine(line, 1, true, {}, {}, playing = true, single = true)
                }
            }
        }
        compose.onNodeWithContentDescription("Play line: Merhaba dünya").assertDoesNotExist()
        compose.onNodeWithContentDescription("Pause line: Merhaba dünya").assertDoesNotExist()
        compose.onNodeWithText("Merhaba").assertIsDisplayed()
        compose.onNodeWithText("Hello world").assertIsDisplayed()
    }

    @Test fun persianTranslationAndForeignLineRemainVisibleInRtl() {
        compose.setContent {
            CompositionLocalProvider(LocalFa provides true, LocalReducedMotion provides true, LocalLayoutDirection provides LayoutDirection.Rtl) {
                ReedanceTheme(Settings(language = "fa")) { LyricLine(line, 1, true, {}, {}) }
            }
        }
        compose.onNodeWithText("سلام دنیا").assertIsDisplayed()
        compose.onNodeWithContentDescription("پخش خط: Merhaba dünya").assertIsDisplayed()
        compose.onNodeWithText("0:04").assertDoesNotExist()
    }
}
