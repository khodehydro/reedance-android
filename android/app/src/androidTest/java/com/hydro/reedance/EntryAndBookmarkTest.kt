package com.hydro.reedance

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.hydro.reedance.data.Settings
import com.hydro.reedance.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class EntryAndBookmarkTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bookmarkTogglesSelectedStateWithReducedMotion() {
        var saved by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalFa provides false, LocalReducedMotion provides true) {
                ReedanceTheme(Settings(language = "en")) { BookmarkButton(saved) { saved = !saved } }
            }
        }
        compose.onNodeWithContentDescription("Bookmark song").assertIsNotSelected().performClick()
        compose.onNodeWithContentDescription("Bookmark song").assertIsSelected().performClick()
        compose.onNodeWithContentDescription("Bookmark song").assertIsNotSelected()
    }

    @Test fun entranceDoesNotKeepPreviousPageOrDuplicateContent() {
        var page by mutableStateOf("home")
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotion provides false) {
                PageEntrance(page) { Column { Text(page) } }
            }
        }
        compose.onNodeWithText("home").assertIsDisplayed()
        compose.runOnIdle { page = "song" }
        compose.onNodeWithText("home").assertDoesNotExist()
        compose.onAllNodesWithText("song").assertCountEquals(1)
        compose.onNodeWithText("song").assertIsDisplayed()
    }
}
