package com.hydro.reedance.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable internal fun DialoguesScreen(pauseSong: () -> Unit, exit: () -> Unit) {
    var categoryIndex by rememberSaveable { mutableIntStateOf(-1) }
    var dialogueIndex by rememberSaveable { mutableIntStateOf(-1) }
    var reader by rememberSaveable { mutableStateOf(false) }
    val category = dialogueCategories.getOrNull(categoryIndex)
    val dialogue = category?.demos?.getOrNull(dialogueIndex)
    fun back() {
        when {
            reader -> reader = false
            dialogueIndex >= 0 -> dialogueIndex = -1
            categoryIndex >= 0 -> categoryIndex = -1
            else -> exit()
        }
    }
    BackHandler { back() }
    Column(Modifier.fillMaxSize().padding(bottom = LocalOverlayBottomInset.current)) {
        HeaderSurface {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                PressIconButton(::back) { AppIcon(UiIcons.Back, tr("بازگشت", "Back")) }
                Text(when {
                    reader && dialogue != null -> tr(dialogue.titleFa, dialogue.titleEn)
                    dialogue != null -> tr("دانلود گفت‌وگو", "Download dialogue")
                    category != null -> tr(category.fa, category.en)
                    else -> tr("گفت‌وگو", "Dialogues")
                }, style = MaterialTheme.typography.titleLarge)
            }
        }
        if(reader && dialogue != null) {
            key(categoryIndex, dialogueIndex) { DialogueReader(dialogue) }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when {
                    dialogue != null -> Panel {
                        Text(tr(dialogue.titleFa, dialogue.titleEn), style = MaterialTheme.typography.headlineSmall)
                        Text(tr("نسخهٔ نمایشی: دانلود و فایل‌های صوتی هنوز متصل نیستند.", "Demo: downloads and recorded audio are not connected yet."))
                        Action(tr("باز کردن دمو", "Open demo"), Modifier.fillMaxWidth()) {
                            pauseSong()
                            reader = true
                        }
                    }
                    category != null -> SettingsSection(tr("گفت‌وگوها", "Dialogues")) {
                        category.demos.forEachIndexed { index, item ->
                            if(index > 0) SettingsDivider()
                            SettingsAction(UiIcons.Headphones, tr(item.titleFa, item.titleEn), tr("دمو", "Demo")) { dialogueIndex = index }
                        }
                    }
                    else -> SettingsSection(tr("دسته‌بندی‌ها", "Categories")) {
                        dialogueCategories.forEachIndexed { index, item ->
                            if(index > 0) SettingsDivider()
                            SettingsAction(UiIcons.Phrases, tr(item.fa, item.en)) { categoryIndex = index }
                        }
                    }
                }
            }
        }
    }
}
