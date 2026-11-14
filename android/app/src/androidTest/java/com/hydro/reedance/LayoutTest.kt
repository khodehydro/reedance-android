package com.hydro.reedance

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import com.google.gson.JsonParser
import com.hydro.reedance.data.Settings
import com.hydro.reedance.ui.*
import org.junit.Rule
import org.junit.Test

class LayoutTest {
    @get:Rule val compose = createComposeRule()
    @Test fun persianTableAndLatinTextRemainReadableInRtl() {
        val table = JsonParser.parseString("""{"titleFa":"جدول واژه‌ها","columns":[{"fa":"واژه","en":"Word"},{"fa":"معنی","en":"Meaning"}],"rows":[["hope",{"fa":"امید","en":"hope"}]]}""").asJsonObject
        compose.setContent {
            CompositionLocalProvider(LocalFa provides true, LocalReducedMotion provides true, LocalLayoutDirection provides LayoutDirection.Rtl) {
                ReedanceTheme(Settings(language = "fa")) { LearningTable(table) }
            }
        }
        compose.onNodeWithText("جدول واژه‌ها").assertIsDisplayed()
        compose.onNodeWithText("hope").assertIsDisplayed()
        compose.onNodeWithText("امید").assertIsDisplayed()
    }
    @Test fun englishTableUsesEnglishLabels() {
        val table = JsonParser.parseString("""{"titleEn":"Words","columns":[{"fa":"واژه","en":"Word"}],"rows":[["sí"]]}""").asJsonObject
        compose.setContent {
            CompositionLocalProvider(LocalFa provides false, LocalReducedMotion provides true, LocalLayoutDirection provides LayoutDirection.Ltr) {
                ReedanceTheme(Settings(language = "en")) { LearningTable(table) }
            }
        }
        compose.onNodeWithText("Word").assertIsDisplayed()
        compose.onNodeWithText("sí").assertIsDisplayed()
    }
    @Test fun flagTabsHaveAccessibleNamesAndUpdateSelection() {
        compose.setContent {
            var selected by remember { mutableIntStateOf(0) }
            CompositionLocalProvider(LocalFa provides true, LocalReducedMotion provides true, LocalLayoutDirection provides LayoutDirection.Rtl) {
                ReedanceTheme(Settings(language = "fa")) {
                    PillTabs(listOf(PillOption("همه", description = "All languages"), PillOption("🇬🇧", description = "English")), selected) { selected = it }
                }
            }
        }
        compose.onNodeWithContentDescription("All languages").assertIsSelected()
        compose.onNodeWithContentDescription("English").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("All languages").assertIsNotSelected()
    }

}
