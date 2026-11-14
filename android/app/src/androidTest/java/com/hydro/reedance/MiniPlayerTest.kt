package com.hydro.reedance

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.hydro.reedance.data.ActivePlayback
import com.hydro.reedance.data.Settings
import com.hydro.reedance.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MiniPlayerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun activeSongControlsNavigationAndCoffeeOpensShareDialog() {
        var active by mutableStateOf(ActivePlayback("gulum-2", "Gülüm II", "Ayaz Erdoğan", null))
        var playing by mutableStateOf(true)
        var opened: String? = null
        compose.setContent {
            CompositionLocalProvider(LocalFa provides false, LocalReducedMotion provides true) {
                ReedanceTheme(Settings(language = "en")) {
                    MiniPlayer(active, playing, { playing = !playing }, { opened = active.songId })
                }
            }
        }
        compose.onNodeWithText("Gülüm II").performClick()
        compose.runOnIdle { assertEquals("gulum-2", opened) }
        compose.onNodeWithContentDescription("Pause").performClick()
        compose.onNodeWithContentDescription("Play").assertIsDisplayed()
        compose.onNodeWithContentDescription("Invite friends").performClick()
        compose.onNodeWithText("Share Reedance").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Not now").performScrollTo().performClick()
        compose.onNodeWithText("Share Reedance").assertDoesNotExist()
        compose.runOnIdle { active = ActivePlayback("midnight-avenue", "Midnight Avenue", "Reedance Originals", null) }
        compose.onNodeWithText("Midnight Avenue").performClick()
        compose.runOnIdle { assertEquals("midnight-avenue", opened) }
    }
}
