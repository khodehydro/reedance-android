package com.hydro.reedance

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.hydro.reedance.data.Settings
import com.hydro.reedance.ui.AppearancePicker
import com.hydro.reedance.ui.LocalFa
import com.hydro.reedance.ui.ReedanceTheme
import org.junit.Rule
import org.junit.Test

class ProfileAppearanceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun previewsSelectOneOfThreeAppearanceModes() {
        val theme = mutableStateOf("dark")
        compose.setContent {
            CompositionLocalProvider(LocalFa provides false) {
                ReedanceTheme(Settings(theme = theme.value)) {
                    AppearancePicker(theme.value) { theme.value = it }
                }
            }
        }
        compose.onNodeWithText("Dark").assertIsSelected()
        compose.onNodeWithText("System").performClick().assertIsSelected()
        compose.onNodeWithText("Light").performClick().assertIsSelected()
        compose.onNodeWithText("System").assertIsNotSelected()
    }
}
