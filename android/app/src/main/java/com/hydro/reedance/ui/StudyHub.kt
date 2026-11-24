package com.hydro.reedance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal const val StudyHubRoute = "study"
internal val studySectionRoutes = setOf("review", "gym", "game", "library")

@Composable internal fun studySectionTitle(route: String): String = when(route) {
    "review" -> tr("مرور", "Review")
    "gym" -> tr("باشگاه", "Gym")
    "game" -> tr("بازی", "Game")
    "library" -> tr("کتابخانه", "Library")
    else -> tr("یادگیری", "Learn")
}

@Composable internal fun StudyHubScreen(open: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp + LocalOverlayBottomInset.current),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Heading(tr("یادگیری", "Learn"), tr("مرور کن، تمرین کن و به ذخیره‌هایت برگرد.", "Review, practise, and revisit what you’ve saved."))
        SettingsSection(tr("بخش‌های یادگیری", "Learning sections")) {
            SettingsAction(UiIcons.Review, studySectionTitle("review")) { open("review") }
            SettingsDivider()
            SettingsAction(UiIcons.Dumbbell, studySectionTitle("gym")) { open("gym") }
            SettingsDivider()
            SettingsAction(UiIcons.Game, studySectionTitle("game")) { open("game") }
            SettingsDivider()
            SettingsAction(UiIcons.Library, studySectionTitle("library")) { open("library") }
        }
    }
}
