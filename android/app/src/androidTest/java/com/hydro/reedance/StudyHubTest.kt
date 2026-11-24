package com.hydro.reedance

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.hydro.reedance.data.Settings
import com.hydro.reedance.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StudyHubTest {
    @get:Rule val compose = createComposeRule()

    @Test fun eachRowOpensItsOwnDestination() {
        val opened = mutableListOf<String>()
        compose.setContent {
            CompositionLocalProvider(LocalFa provides false) {
                ReedanceTheme(Settings()) { StudyHubScreen { opened.add(it) } }
            }
        }
        listOf("Review", "Gym", "Game", "Library").forEach { compose.onNodeWithText(it).performClick() }
        compose.runOnIdle { assertEquals(listOf("review", "gym", "game", "library"), opened) }
    }
}
