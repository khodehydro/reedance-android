package com.hydro.reedance.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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

@Composable internal fun DialoguesScreen(pauseSong: () -> Unit, initialCategory: Int, exit: () -> Unit) {
    var categoryIndex by rememberSaveable { mutableIntStateOf(initialCategory) }
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
    var paperIndex by rememberDialoguePreference("paper", 0, 0..2)
    var dim by rememberDialogueToggle("dim", false)
    DialoguePalette(enabled = reader && dialogue != null, paperIndex = paperIndex, dim = dim) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
            HeaderSurface {
                Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PressIconButton(::back) { AppIcon(UiIcons.Back, tr("بازگشت", "Back")) }
                    Text(when {
                        reader && dialogue != null -> tr(dialogue.titleFa, dialogue.titleEn)
                        dialogue != null -> tr("دانلود گفت‌وگو", "Download dialogue")
                        category != null -> tr(category.fa, category.en)
                        else -> ReeTalkName
                    }, style = MaterialTheme.typography.titleLarge)
                }
            }
            if(reader && dialogue != null) {
                key(categoryIndex, dialogueIndex) {
                    DialogueReader(dialogue, paperIndex, dim, onPaperChange = { paperIndex = (paperIndex + 1) % 3 },
                        onDimChange = { dim = !dim })
                }
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
}

@Composable private fun DialogueArtworkCard(@DrawableRes artwork: Int, title: String, detail: String, modifier: Modifier = Modifier.fillMaxWidth(), open: () -> Unit) {
    Surface(onClick = open, modifier = modifier, shape = GroupedCardShape) {
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

@Composable internal fun HomeDialogueCategories(open: (Int) -> Unit) {
    if(dialogueCategories.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(ReeTalkName, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            PressTextButton({ open(-1) }) { Text(tr("دیدن همه", "See all")) }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Demo catalog entries are listed in publication order (oldest first).
            items(dialogueCategories.indices.reversed().take(2), key = { it }) { index ->
                val category = dialogueCategories[index]
                val count = category.demos.size
                DialogueArtworkCard(category.artwork, tr(category.fa, category.en),
                    tr("$count گفت‌وگو", if(count == 1) "1 dialogue" else "$count dialogues"), Modifier.width(236.dp)) { open(index) }
            }
        }
    }
}
