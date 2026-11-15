package com.hydro.reedance

import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.gson.JsonParser
import com.hydro.reedance.data.Settings
import com.hydro.reedance.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MatchBoardTest {
    @get:Rule val compose = createComposeRule()
    private val question = JsonParser.parseString("""{"pairs":[{"id":"a","text":"bir","en":"one"},{"id":"b","text":"iki","en":"two"}],"meaningOrder":["b","a"]}""").asJsonObject

    @Test fun connectsFromEitherSideAndMovingMeaningDisconnectsPreviousWord() {
        var answer by mutableStateOf<List<String>>(emptyList())
        compose.setContent {
            CompositionLocalProvider(LocalFa provides false, LocalReducedMotion provides true) {
                ReedanceTheme(Settings(language = "en")) {
                    MatchBoard(question, answer, false, true, { answer = it })
                }
            }
        }
        compose.onNodeWithText("bir").performClick()
        compose.onNodeWithText("one").performClick()
        compose.runOnIdle { assertEquals(listOf("a", ""), answer) }
        compose.onNodeWithText("one").performClick()
        compose.onNodeWithText("iki").performClick()
        compose.runOnIdle { assertEquals(listOf("", "a"), answer) }
    }

    @Test fun checkedBoardShowsPerConnectionFeedbackAndLocksInput() {
        compose.setContent {
            CompositionLocalProvider(LocalFa provides false, LocalReducedMotion provides true) {
                ReedanceTheme(Settings(language = "en")) {
                    MatchBoard(question, listOf("b", "a"), true, false, { error("Read-only board") })
                }
            }
        }
        compose.onNodeWithText("✕ bir").assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Incorrect"))
        compose.onNodeWithText("✕ two").assertIsNotEnabled()
    }
}
