package com.hydro.reedance.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydro.reedance.domain.PremiumEntitlement

@Composable internal fun DialoguesScreen(pauseSong: () -> Unit, initialCategory: Int, exit: () -> Unit, openPremium: () -> Unit) {
    var categoryIndex by rememberSaveable { mutableIntStateOf(initialCategory) }
    var dialogueIndex by rememberSaveable { mutableIntStateOf(-1) }
    var reader by rememberSaveable { mutableStateOf(false) }
    val category = dialogueCategories.getOrNull(categoryIndex)
    val dialogue = category?.demos?.getOrNull(dialogueIndex)
    val subscribed by PremiumEntitlement.active.collectAsStateWithLifecycle()
    val lockedCategory = category?.premium == true && !subscribed
    val lockedDialogue = dialogue?.premium == true && !subscribed
    // Restored navigation or an entitlement change must not expose a locked detail screen.
    if(lockedCategory || lockedDialogue) {
        LaunchedEffect(lockedCategory, lockedDialogue) {
            reader = false
            dialogueIndex = -1
            if(lockedCategory) categoryIndex = -1
            openPremium()
        }
        return
    }
    fun back() {
        when {
            reader -> reader = false
            dialogueIndex >= 0 -> dialogueIndex = -1
            categoryIndex >= 0 -> categoryIndex = -1
            else -> exit()
        }
    }
    BackHandler { back() }
    var paperIndex by rememberDialoguePreference("paper", 0, DialoguePaper.entries.indices)
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
                    DialogueReader(dialogue, paperIndex, dim, onPaperChange = { paperIndex = (paperIndex + 1) % DialoguePaper.entries.size },
                        onDimChange = { dim = !dim })
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when {
                        dialogue != null -> Panel {
                            DialogueCover(dialogue.artwork, Modifier.fillMaxWidth().aspectRatio(5f / 3f).clip(InsetControlShape))
                            SongTitle(tr(dialogue.titleFa, dialogue.titleEn), category?.premium == true || dialogue.premium, MaterialTheme.typography.headlineSmall)
                            Text(tr("نسخهٔ نمایشی: دانلود و فایل‌های صوتی هنوز متصل نیستند.", "Demo: downloads and recorded audio are not connected yet."))
                            Action(tr("باز کردن دمو", "Open demo"), Modifier.fillMaxWidth()) {
                                if((category?.premium == true || dialogue.premium) && !PremiumEntitlement.active.value) {
                                    dialogueIndex = -1
                                    if(category?.premium == true) categoryIndex = -1
                                    openPremium()
                                } else {
                                    pauseSong()
                                    reader = true
                                }
                            }
                        }
                        category != null -> {
                            Heading(tr("گفت‌وگوها", "Dialogues"))
                            if(category.demos.isEmpty()) Panel {
                                Text(tr("هنوز گفت‌وگویی برای این موضوع منتشر نشده است.", "No dialogues have been published for this topic yet."))
                            }
                            category.demos.forEachIndexed { index, item ->
                                DialogueArtworkCard(item.artwork, tr(item.titleFa, item.titleEn), tr("دمو", "Demo"), premium = category.premium || item.premium) {
                                    if(item.premium && !PremiumEntitlement.active.value) openPremium()
                                    else dialogueIndex = index
                                }
                            }
                        }
                        else -> {
                            Heading(tr("دسته‌بندی‌ها", "Categories"))
                            dialogueCategories.forEachIndexed { index, item ->
                                val count = item.demos.size
                                DialogueArtworkCard(item.artwork, tr(item.fa, item.en),
                                    tr("$count گفت‌وگو", if(count == 1) "1 dialogue" else "$count dialogues"), premium = item.premium) {
                                    if(item.premium && !PremiumEntitlement.active.value) openPremium()
                                    else categoryIndex = index
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun DialogueArtworkCard(@DrawableRes artwork: Int, title: String, detail: String, modifier: Modifier = Modifier.fillMaxWidth(), premium: Boolean = false, open: () -> Unit) {
    Surface(onClick = open, modifier = modifier, shape = GroupedCardShape) {
        Column {
            DialogueCover(artwork, Modifier.fillMaxWidth().aspectRatio(2f))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { SongTitle(title, premium, MaterialTheme.typography.titleMedium) }
                Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable internal fun DialogueCover(@DrawableRes artwork: Int, modifier: Modifier) {
    Image(painterResource(artwork), contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
}

@Composable internal fun HomeDialogueCategories(open: (Int) -> Unit) {
    if(dialogueCategories.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        items(dialogueCategories.indices.toList(), key = { it }) { index ->
            val category = dialogueCategories[index]
            Surface(onClick = { open(index) }, shape = CircleShape, color = colors.surfaceContainerHigh) {
                Row(Modifier.padding(start = 6.dp, top = 6.dp, end = 15.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    DialogueCover(category.artwork, Modifier.size(width = 62.dp, height = 46.dp).clip(CircleShape))
                    Text(tr(category.fa, category.en), style = MaterialTheme.typography.titleSmall)
                    if(category.premium) AppIcon(UiIcons.CrownFilled, tr("پریمیوم", "Premium"), Modifier.size(14.dp))
                }
            }
        }
        item(key = "more") {
            Surface(onClick = { open(-1) }, shape = CircleShape, color = colors.surfaceContainerHigh) {
                Box(Modifier.heightIn(min = 58.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text(tr("بیشتر", "More"), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}
