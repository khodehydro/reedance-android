package com.hydro.reedance

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.gson.JsonParser
import com.hydro.reedance.data.Settings
import com.hydro.reedance.domain.Grade
import com.hydro.reedance.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PracticeChoicesTest {
    @get:Rule val compose = createComposeRule()
    private val question = JsonParser.parseString("""{"type":"choice","correctId":"yes","options":[{"id":"yes","en":"Right"},{"id":"no","en":"Wrong"}]}""").asJsonObject

    private fun showAndChoose(id: String) {
        var selected by mutableStateOf<String?>(null)
        var result by mutableStateOf<Grade?>(null)
        var submissions = 0
        compose.setContent {
            CompositionLocalProvider(LocalFa provides false, LocalReducedMotion provides true) {
                ReedanceTheme(Settings(language = "en")) {
                    Column {
                        PracticeChoices(question, selected, result, false) {
                            submissions++
                            selected = it
                            result = Grade(it == "yes", emptySet())
                        }
                    }
                }
            }
        }
        compose.onNodeWithText(if(id == "yes") "○ Right" else "○ Wrong").performClick()
        compose.onNodeWithText("✓ Right").assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Correct answer"))
        if(id == "no") compose.onNodeWithText("✕ Wrong").assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Incorrect answer"))
        compose.runOnIdle { assertEquals(1, submissions) }
    }

    @Test fun correctChoiceGetsSuccessFeedbackAndLocksOptions() { showAndChoose("yes") }
    @Test fun wrongChoiceRevealsCorrectAnswerAndLocksOptions() { showAndChoose("no") }
}
