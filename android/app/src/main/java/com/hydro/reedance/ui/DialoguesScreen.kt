package com.hydro.reedance.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
                        DialogueCover(dialogue.artwork, Modifier.fillMaxWidth().aspectRatio(5f / 3f).clip(InsetControlShape))
                        Text(tr(dialogue.titleFa, dialogue.titleEn), style = MaterialTheme.typography.headlineSmall)
                        Text(tr("نسخهٔ نمایشی: دانلود و فایل‌های صوتی هنوز متصل نیستند.", "Demo: downloads and recorded audio are not connected yet."))
                        Action(tr("باز کردن دمو", "Open demo"), Modifier.fillMaxWidth()) {
                            pauseSong()
                            reader = true
                        }
                    }
                    category != null -> {
                        Heading(tr("گفت‌وگوها", "Dialogues"))
                        category.demos.forEachIndexed { index, item ->
                            DialogueArtworkCard(item.artwork, tr(item.titleFa, item.titleEn), tr("دمو", "Demo")) { dialogueIndex = index }
                        }
                    }
                    else -> {
                        Heading(tr("دسته‌بندی‌ها", "Categories"))
                        dialogueCategories.forEachIndexed { index, item ->
                            val count = item.demos.size
                            DialogueArtworkCard(item.artwork, tr(item.fa, item.en),
                                tr("$count گفت‌وگو", if(count == 1) "1 dialogue" else "$count dialogues")) { categoryIndex = index }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun DialogueArtworkCard(@DrawableRes artwork: Int, title: String, detail: String, open: () -> Unit) {
    Surface(onClick = open, modifier = Modifier.fillMaxWidth(), shape = GroupedCardShape) {
        Column {
            DialogueCover(artwork, Modifier.fillMaxWidth().aspectRatio(2f))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun DialogueCover(@DrawableRes artwork: Int, modifier: Modifier) {
    Image(painterResource(artwork), contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
}
