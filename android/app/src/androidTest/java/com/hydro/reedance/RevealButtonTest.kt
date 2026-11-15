package com.hydro.reedance

import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.hydro.reedance.data.Settings
import com.hydro.reedance.ui.*
import org.junit.Rule
import org.junit.Test

class RevealButtonTest {
    @get:Rule val compose = createComposeRule()
    @Test fun answerToggleUpdatesActionLabelAndAccessibleState() {
        var revealed by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalFa provides false, LocalReducedMotion provides true) {
                ReedanceTheme(Settings(language = "en")) { RevealButton(revealed, { revealed = !revealed }) }
            }
        }
        compose.onNodeWithText("Reveal answer")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Hidden")).performClick()
        compose.onNodeWithText("Hide answer")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Revealed")).performClick()
        compose.onNodeWithText("Reveal answer").assertIsDisplayed()
    }
}
