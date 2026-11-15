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
    private val question = JsonParser.parseString("""{"pairs":[{"id":"a","text":"bir","en":"one","fa":"یک"},{"id":"b","text":"iki","en":"two","fa":"دو"}],"meaningOrder":["b","a"]}""").asJsonObject

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
        compose.onNodeWithText("یک").performClick()
        compose.runOnIdle { assertEquals(listOf("a", ""), answer) }
        compose.onNodeWithText("یک").performClick()
        compose.onNodeWithText("iki").performClick()
        compose.runOnIdle { assertEquals(listOf("", "a"), answer) }
    }

    @Test fun swappedDuplicateMeaningsReceiveGreenFeedback() {
        val duplicates = JsonParser.parseString("""{"pairs":[{"id":"a","text":"kalp","en":"heart","fa":"قلب"},{"id":"b","text":"gönül","en":"heart","fa":"قلب"}]}""").asJsonObject
        compose.setContent {
            CompositionLocalProvider(LocalFa provides false, LocalReducedMotion provides true) {
                ReedanceTheme(Settings(language = "en")) {
                    MatchBoard(duplicates, listOf("b", "a"), true, false, {})
                }
            }
        }
        compose.onNodeWithText("✓ kalp")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Correct"))
        compose.onNodeWithText("✓ gönül")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Correct"))
        compose.onAllNodesWithText("✓ قلب").assertCountEquals(2)
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
        compose.onNodeWithText("✕ دو").assertIsNotEnabled()
    }
}
