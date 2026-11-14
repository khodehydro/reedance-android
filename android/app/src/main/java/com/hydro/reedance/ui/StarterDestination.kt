package com.hydro.reedance.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hydro.reedance.R
import com.hydro.reedance.data.AppModel

private val courseFlags = listOf("en" to R.drawable.flag_gb_circle, "es" to R.drawable.flag_es_circle, "ko" to R.drawable.flag_kr_circle)
@Composable private fun courseLanguage(code: String) = when(code) {
    "es" -> tr("اسپانیایی", "Spanish")
    "ko" -> tr("کره‌ای", "Korean")
    else -> tr("انگلیسی", "English")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun StarterDestination(model: AppModel, back: () -> Unit) {
    var language by rememberSaveable { mutableStateOf(model.settings.value.targetLanguage.takeIf { it in listOf("en", "es", "ko") } ?: "en") }
    var choosing by rememberSaveable { mutableStateOf(false) }
    val selected = courseFlags.firstOrNull { it.first == language } ?: courseFlags.first()
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        HeaderSurface {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                PressIconButton(back, Modifier.size(48.dp)) { AppIcon(UiIcons.Back, tr("بازگشت", "Back")) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(tr("استارتر", "Starter"), style = MaterialTheme.typography.titleLarge)
                }
                PressIconButton({ choosing = true }, Modifier.size(48.dp)) {
                    Image(painterResource(selected.second), tr("انتخاب زبان آموزش", "Choose learning language") + ": " + courseLanguage(selected.first), Modifier.size(32.dp).clip(CircleShape))
                }
            }
        }
    }) { padding ->
        CompositionLocalProvider(LocalOverlayBottomInset provides padding.calculateBottomPadding()) {
            Box(Modifier.fillMaxSize().padding(
                start = padding.calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
                end = padding.calculateEndPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
                top = padding.calculateTopPadding()
            )) { PageEntrance("starter:${selected.first}") { StarterScreen(model, selected.first) } }
        }
    }
    if(choosing) ReedanceBottomSheet(onDismissRequest = { choosing = false }) {
        val dismissSheet = LocalSheetDismiss.current
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("زبان آموزش استارتر", "Starter learning language"), style = MaterialTheme.typography.titleLarge)
            courseFlags.forEach { (code, flag) ->
                Row(Modifier.fillMaxWidth().clip(CircleShape).selectable(code == selected.first, role = Role.RadioButton,
                    onClick = { dismissSheet { language = code } }).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Image(painterResource(flag), null, Modifier.size(36.dp).clip(CircleShape))
                    Text(courseLanguage(code), Modifier.weight(1f))
                    PressRadioButton(code == selected.first, null)
                }
            }
        }
    }
}
