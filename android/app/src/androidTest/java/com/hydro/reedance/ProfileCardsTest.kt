package com.hydro.reedance

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.hydro.reedance.data.Settings
import com.hydro.reedance.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProfileCardsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun profileCardsExposeUpgradeAndStarterWithoutPretendingPurchaseIsReady() {
        var upgrades = 0
        var starters = 0
        compose.setContent {
            CompositionLocalProvider(LocalFa provides false, LocalReducedMotion provides true) {
                ReedanceTheme(Settings(language = "en")) {
                    Column {
                        ProfileMembershipCard { upgrades++ }
                        ProfileStarterCard { starters++ }
                    }
                }
            }
        }
        compose.onNodeWithText("Membership checkout is not available yet.").assertIsDisplayed()
        compose.onNodeWithText("Get Premium").performClick()
        compose.onNodeWithText("Open Starter").performClick()
        compose.runOnIdle { assertEquals(1, upgrades); assertEquals(1, starters) }
        compose.onNodeWithText("Hello, guest").assertDoesNotExist()
    }
}
