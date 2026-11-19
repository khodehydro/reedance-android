package com.hydro.reedance

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.hydro.reedance.ui.LocalFa
import com.hydro.reedance.ui.PremiumScreen
import org.junit.Rule
import org.junit.Test

class PremiumScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun plansShowTomanPricesAndSubscribeUsesTheSelectedPlan() {
        compose.setContent {
            CompositionLocalProvider(LocalFa provides false) { PremiumScreen {} }
        }
        compose.onNodeWithText("One month free").assertExists()
        compose.onNodeWithText("560,000").assertExists()
        compose.onNodeWithText("Monthly").performScrollTo().performClick()
        compose.onNodeWithText("Subscribe").performScrollTo().performClick()
        compose.onNodeWithText("Monthly · 280,000 toman", substring = true).assertIsDisplayed()
    }
}
