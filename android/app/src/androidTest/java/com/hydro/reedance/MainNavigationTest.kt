package com.hydro.reedance

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.hydro.reedance.data.AppModel
import com.hydro.reedance.data.Settings
import com.hydro.reedance.ui.*
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** Exercise the real shell, saveable destination state and menu callbacks, not a mock router. */
class MainNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val models = ViewModelStore()
    @After fun cleanup() { models.clear() }

    @Test fun repeatedlyLeavesHomeAndReturnsWithEntranceAnimationsEnabled() {
        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(ApplicationProvider.getApplicationContext<Application>())
        val model = ViewModelProvider(models, factory)[AppModel::class.java]
        compose.setContent {
            CompositionLocalProvider(LocalFa provides false, LocalReducedMotion provides false) {
                ReedanceTheme(Settings(language = "en")) {
                    // Pause continuous decorative loops for deterministic idling;
                    // finite page/card entrance animations remain enabled.
                    val motion = remember { BackgroundMotion().apply { overlays = 1 } }
                    CompositionLocalProvider(LocalBackgroundMotion provides motion) { MainShell(model) }
                }
            }
        }
        repeat(2) {
            compose.onNodeWithContentDescription("Explore").performClick()
            compose.onNodeWithText("Discover music").assertIsDisplayed()
            compose.onNodeWithText("Every song.\nOne step further.").assertDoesNotExist()
            compose.onNodeWithContentDescription("Review").performClick()
            compose.onNodeWithText("Words that stay").assertIsDisplayed()
            compose.onNodeWithContentDescription("Library").performClick()
            compose.onNodeWithText("Your little collection").assertIsDisplayed()
            compose.onNodeWithContentDescription("My space and settings").performClick()
            compose.onNodeWithText("My space").assertIsDisplayed()
            compose.onNodeWithContentDescription("Home").performClick()
            compose.onNodeWithText("Every song.\nOne step further.").assertExists()
            compose.onNodeWithText("Your little collection").assertDoesNotExist()
        }
    }
}
