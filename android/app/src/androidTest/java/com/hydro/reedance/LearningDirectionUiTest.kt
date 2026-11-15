package com.hydro.reedance

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.hydro.reedance.data.Settings
import com.hydro.reedance.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LearningDirectionUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun mixedPersianParagraphRemainsRtlInEnglishInterface() {
        val persian = "kalp یعنی قلب؛ معنی واژه را بنویس (۱)."
        val original = "One step at a time."
        compose.setContent {
            CompositionLocalProvider(LocalFa provides false, LocalReducedMotion provides true) {
                ReedanceTheme(Settings(language = "en")) {
                    Column {
                        PersianText(persian)
                        Foreign(original)
                        PracticeTypingField("gönül", true, {})
                    }
                }
            }
        }
        val learningLayout = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(persian).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(learningLayout) }
        val originalLayout = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(original).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(originalLayout) }
        compose.runOnIdle {
            assertEquals(ResolvedTextDirection.Rtl, learningLayout.single().getParagraphDirection(0))
            assertEquals(TextAlign.Right, learningLayout.single().layoutInput.style.textAlign)
            assertEquals(ResolvedTextDirection.Ltr, originalLayout.single().getParagraphDirection(0))
            assertEquals(TextAlign.Left, originalLayout.single().layoutInput.style.textAlign)
        }
    }
}
