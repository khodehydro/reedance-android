package com.hydro.reedance

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.hydro.reedance.data.Settings
import com.hydro.reedance.ui.*
import org.junit.Rule
import org.junit.Test

class BookmarkMotionTest {
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

}
