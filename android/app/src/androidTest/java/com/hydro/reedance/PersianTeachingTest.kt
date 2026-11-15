package com.hydro.reedance

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.gson.JsonParser
import com.hydro.reedance.data.Settings
import com.hydro.reedance.ui.*
import org.junit.Rule
import org.junit.Test

class PersianTeachingTest {
    @get:Rule val compose = createComposeRule()
    @Test fun englishInterfaceStillShowsPersianMeaningsAndLearningNotes() {
        val question = JsonParser.parseString("""{"correctId":"a","options":[{"id":"a","fa":"قلب","en":"heart"}]}""").asJsonObject
        val note = JsonParser.parseString("""{"titleFa":"نکته فارسی","titleEn":"English note","bodyFa":"توضیح فارسی","bodyEn":"English explanation"}""").asJsonObject
        compose.setContent {
            CompositionLocalProvider(LocalFa provides false, LocalReducedMotion provides true) {
                ReedanceTheme(Settings(language = "en")) {
                    Column {
                        Text(tr("ادامه", "Continue"))
                        PracticeChoices(question, null, null, false, {})
                        LearningNote(note)
                    }
                }
            }
        }
        compose.onNodeWithText("Continue").assertIsDisplayed()
        compose.onNodeWithText("○ قلب").assertIsDisplayed()
        compose.onNodeWithText("نکته فارسی").assertIsDisplayed()
        compose.onNodeWithText("توضیح فارسی").assertIsDisplayed()
        compose.onNodeWithText("○ heart").assertDoesNotExist()
        compose.onNodeWithText("English explanation").assertDoesNotExist()
    }
}
