package com.hydro.reedance

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.gson.JsonParser
import com.hydro.reedance.data.Settings
import com.hydro.reedance.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SentenceOrderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tappingSentencePartReturnsOnlyThatPartToItsOriginalOptionPosition() {
        val question = JsonParser.parseString("""{"chunks":[{"id":"one","text":"I"},{"id":"two","text":"love"},{"id":"three","text":"music"}],"presentationOrder":["three","one","two"]}""").asJsonObject
        var answer by mutableStateOf<List<String>>(emptyList())
        compose.setContent {
            CompositionLocalProvider(LocalFa provides false, LocalReducedMotion provides true) {
                ReedanceTheme(Settings(language = "en")) {
                    Column { SentenceOrder(question, listOf("three", "one", "two"), answer, null, false) { answer = it } }
                }
            }
        }
        compose.onNodeWithText("I").performClick()
        compose.onNodeWithText("love").performClick()
        compose.onNodeWithText("music").performClick()
        compose.runOnIdle { assertEquals(listOf("one", "two", "three"), answer) }
        compose.onNodeWithText("love").performClick()
        compose.runOnIdle { assertEquals(listOf("one", "three"), answer) }
        compose.onNodeWithText("love").performClick()
        compose.runOnIdle { assertEquals(listOf("one", "three", "two"), answer) }
        compose.onNodeWithText("Reset order").assertDoesNotExist()
        compose.onNodeWithText("Tap a part of your sentence to return it to the options.").assertDoesNotExist()
    }
}
