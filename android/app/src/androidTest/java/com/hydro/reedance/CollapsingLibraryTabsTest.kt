package com.hydro.reedance

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.hydro.reedance.data.Settings
import com.hydro.reedance.ui.*
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CollapsingLibraryTabsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun compactTabsHideLabelsButRetainAccessibleSelectionAndExpandAgain() {
        var compact by mutableStateOf(false)
        var selected by mutableIntStateOf(0)
        compose.setContent {
            CompositionLocalProvider(LocalFa provides false, LocalReducedMotion provides false) {
                ReedanceTheme(Settings(language = "en")) {
                    Box(Modifier.fillMaxWidth().testTag("library-tab-slot")) {
                    CollapsingPillTabs(listOf(
                        PillOption("Words", UiIcons.Words, filledIcon = UiIcons.WordsFilled),
                        PillOption("Notes", UiIcons.Notes, filledIcon = UiIcons.NotesFilled)
                    ), selected, compact) { selected = it }
                    }
                }
            }
        }
        val expandedSlot = compose.onNodeWithTag("library-tab-slot").fetchSemanticsNode().boundsInRoot
        val expandedWidth = compose.onNodeWithContentDescription("Words").fetchSemanticsNode().boundsInRoot.width
        compose.runOnIdle { compact = true }
        compose.waitForIdle()
        compose.onNodeWithText("Words").assertDoesNotExist()
        compose.onNodeWithText("Notes").assertDoesNotExist()
        val compactWidth = compose.onNodeWithContentDescription("Words").fetchSemanticsNode().boundsInRoot.width
        assertTrue(compactWidth < expandedWidth)
        val compactSlot = compose.onNodeWithTag("library-tab-slot").fetchSemanticsNode().boundsInRoot
        assertEquals(expandedSlot.height, compactSlot.height, 0f)
        assertEquals(expandedSlot.width, compactSlot.width, 0f)
        val wordsBounds = compose.onNodeWithContentDescription("Words").fetchSemanticsNode().boundsInRoot
        val notesBounds = compose.onNodeWithContentDescription("Notes").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(root.center.x, (wordsBounds.left + notesBounds.right) / 2f, 2f)
        compose.onNodeWithContentDescription("Notes").performClick().assertIsSelected()
        compose.runOnIdle { compact = false }
        compose.waitForIdle()
        compose.onNodeWithText("Notes").assertIsDisplayed()
        compose.onNodeWithContentDescription("Notes").assertIsSelected()
    }
}
