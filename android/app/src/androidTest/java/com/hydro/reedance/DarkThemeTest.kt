package com.hydro.reedance

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import com.hydro.reedance.data.Settings
import com.hydro.reedance.ui.ReedanceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DarkThemeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun explicitDarkModeUsesCharcoalRatherThanBlueSurfaces() {
        var background: Color? = null
        var card: Color? = null
        var raised: Color? = null
        var tint: Color? = null
        compose.setContent {
            ReedanceTheme(Settings(theme = "dark")) {
                val colors = MaterialTheme.colorScheme
                SideEffect {
                    background = colors.background
                    card = colors.surface
                    raised = colors.surfaceContainerHigh
                    tint = colors.surfaceTint
                }
            }
        }
        compose.runOnIdle {
            assertEquals(Color(0xFF121212), background)
            assertEquals(Color(0xFF1C1C1C), card)
            assertEquals(Color(0xFF282828), raised)
            assertEquals(Color.Transparent, tint)
        }
    }
}
