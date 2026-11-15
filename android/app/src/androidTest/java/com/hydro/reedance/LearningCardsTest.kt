package com.hydro.reedance

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.gson.JsonParser
import com.hydro.reedance.data.Settings
import com.hydro.reedance.ui.*
import org.junit.Rule
import org.junit.Test

class LearningCardsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun wordShowsDistinctMeaningPronunciationAndExampleSectionsWithEnglishUi() {
        val word = JsonParser.parseString("""{"lemma":"almak","translationFa":"گرفتن","faReading":"آلماک","partFa":"فعل","example":{"text":"Bir kitap almak.","translationFa":"گرفتن یک کتاب."}}""").asJsonObject
        compose.setContent {
            CompositionLocalProvider(LocalFa provides false, LocalReducedMotion provides true) {
                ReedanceTheme(Settings(language = "en")) { WordLearningContent(word) }
            }
        }
        for(text in listOf("almak", "فعل", "معنی", "گرفتن", "تلفظ نوشتاری", "آلماک", "مثال در جمله", "Bir kitap almak.", "گرفتن یک کتاب.")) {
            compose.onNodeWithText(text).assertIsDisplayed()
        }
    }

    @Test fun absentPronunciationAndExampleDoNotLeaveEmptySections() {
        val word = JsonParser.parseString("""{"lemma":"almak","translationFa":"گرفتن"}""").asJsonObject
        compose.setContent {
            CompositionLocalProvider(LocalFa provides false, LocalReducedMotion provides true) {
                ReedanceTheme(Settings(language = "en")) { WordLearningContent(word) }
            }
        }
        compose.onNodeWithText("معنی").assertIsDisplayed()
        compose.onNodeWithText("تلفظ نوشتاری").assertDoesNotExist()
        compose.onNodeWithText("مثال در جمله").assertDoesNotExist()
    }
}
