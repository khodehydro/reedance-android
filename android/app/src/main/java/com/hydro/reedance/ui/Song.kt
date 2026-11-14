package com.hydro.reedance.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.JsonObject
import com.hydro.reedance.BuildConfig
import com.hydro.reedance.data.*
import com.hydro.reedance.domain.LessonAccess

fun clock(ms: Long): String = "%d:%02d".format(ms / 60000, ms / 1000 % 60)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun SongScreen(model: AppModel, songId: String, tab: Int, setTab: (Int) -> Unit, back: () -> Unit, quiz: () -> Unit) {
    val catalog by model.catalog.collectAsStateWithLifecycle()
    val lesson by model.lesson.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val activeLineId by model.activeLineId.collectAsStateWithLifecycle()
    val playing by model.playing.collectAsStateWithLifecycle()
    val bookmarks by model.bookmarks.collectAsStateWithLifecycle()
    val song = catalog.find { it.s("id") == songId } ?: JsonObject()
    val access = LessonAccess(premium = song.s("access") == "paid")
    var subscriptionInfo by remember { mutableStateOf(false) }
    val openQuiz = { if(access.preview) subscriptionInfo = true else quiz() }
    val fa = LocalFa.current
    var mode by rememberSaveable { mutableIntStateOf(1) }
    var selectedWord by remember { mutableStateOf<JsonObject?>(null) }
    val scroll = rememberLazyListState()
    val collapsed by remember { derivedStateOf { scroll.firstVisibleItemIndex > 0 || scroll.firstVisibleItemScrollOffset > 300 } }
    val available = lesson?.s("songId") == songId
    LaunchedEffect(songId) { model.openInstalled(songId) }
    LaunchedEffect(available, lesson) { if(available) model.preparePlayback() }
    val lines = remember(lesson) { lesson?.a("lyrics").orEmpty() }
    val wordsById = remember(lesson, access) { lesson?.a("vocabulary").orEmpty().take(access.limit("vocabulary")).associateBy { it.s("lexemeId") } }
    Column(Modifier.fillMaxSize()) {
        HeaderSurface {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PressIconButton(back) { AppIcon(UiIcons.Back, tr("بازگشت", "Back")) }
                    Column(Modifier.weight(1f)) {
                        SongTitle(song.s("title"), premium = access.premium, style = MaterialTheme.typography.titleMedium)
                        Text("${song.s("language").uppercase()} · ${song.s("level")} · ${song.s("duration")}", style = MaterialTheme.typography.labelSmall)
                    }
                    PressIconButton({ model.toggleBookmark("song", songId) }) {
                        AppIcon(UiIcons.Bookmark, tr("ذخیره", "Save"), tint = if(bookmarks.any { it.itemType == "song" && it.itemId == songId }) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                AnimatedVisibility(collapsed && available) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(song.s("title"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        PressIconButton(model::togglePlay) { AppIcon(if(playing) UiIcons.Pause else UiIcons.Play, tr("پخش یا مکث", "Play or pause")) }
                    }
                }
            }
        }
        LazyColumn(state = scroll, modifier = Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if(!available) {
                item(key = "install") { Panel {
                    CoverImage(if(song.s("distributionRef") == "gulum-ii") "pending-gulum-ii" else songId, song.s("title"), Modifier.fillMaxWidth().height(200.dp))
                    Heading(song.s("title"), song.s("artist"))
                    if(songId == "midnight-avenue" && BuildConfig.DEBUG) {
                        Text(tr("صوت و تمام آموزش‌ها را یک‌بار در فضای خصوصی برنامه نصب کن؛ سپس آفلاین تمرین کن.", "Install the audio and lessons into private storage once, then practice offline."))
                        Text(tr("نسخهٔ آزمایشی: این دمو همراه APK است؛ دانلود شبکه یا بستهٔ امضاشده نیست.", "Debug demo: bundled with the APK, not a network download or a signed production pack."), style = MaterialTheme.typography.bodySmall)
                        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Action(tr("نصب و باز کردن دموی محلی", "Install and open local demo"), enabled = !busy, onClick = model::installDemo)
                    } else if(song.s("distributionRef").isNotEmpty()) {
                        val status by model.installProgress.collectAsStateWithLifecycle()
                        if(busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(status) }
                        Action(tr("دانلود و نصب بسته", "Download and install package"), enabled = !busy, onClick = { model.installPackage(songId) })
                        if(busy) PressTextButton(model::cancelInstall) { Text(tr("لغو", "Cancel")) }
                        Text(tr("برای نصب، manifest امضاشده و کلید توزیع ناشر باید تنظیم شده باشند.", "Installation requires the publisher's signed manifest and distribution key."))
                    } else Text(tr("این عنوان فقط پیش‌نمایش است. صوت و مجوز انتشار برای آن ارائه نشده است.", "Catalog preview only. Licensed audio and a published package have not been supplied."))
                }
                }
            } else {
                val data = lesson!!
                item(key = "player", contentType = "player") { PlayerPanel(model) }
                item(key = "credits") { Text(data.obj("credits").tr("notice", fa), style = MaterialTheme.typography.bodySmall) }
                item(key = "tabs") { ChoiceRow(listOf(tr("لیریک", "Lyrics"), tr("نکته‌ها", "Notes"), tr("لغت‌ها", "Words"), tr("عبارت‌ها", "Phrases")), tab, setTab) }
                item(key = "quiz") { PressOutlinedButton(openQuiz, Modifier.fillMaxWidth()) { AppIcon(UiIcons.Quiz, null); Text(tr("آزمون آهنگ", "Song quiz")) } }
                when(tab) {
                    0 -> {
                        item(key = "lyric-mode") { ChoiceRow(listOf(tr("متن اصلی", "Original"), tr("ترجمهٔ خطی", "Line by line"), tr("کلمه‌به‌کلمه", "Word by word")), mode) { mode = it } }
                        item(key = "lyric-help") { Text(tr("روی زمان برای جابه‌جایی و روی واژه برای معنی بزن.", "Tap a timestamp to seek, or a word to explore it."), style = MaterialTheme.typography.bodySmall) }
                        items(lines.take(access.limit("lyrics")), key = { "lyric-${it.s("id")}" }, contentType = { "lyric" }) { line ->
                            val active = activeLineId == line.s("id")
                            Panel(tone = if(active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
                                PressTextButton({ model.seek(line.n("startMs")) }) { Text(clock(line.n("startMs"))) }
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        line.a("tokens").forEach { token ->
                                            Column(Modifier.clickable {
                                                if(access.preview && token.s("lexemeId") !in wordsById) subscriptionInfo = true
                                                else selectedWord = wordsById[token.s("lexemeId")] ?: JsonObject().apply { addProperty("lemma", token.s("surface")); addProperty("translationFa", token.s("translationFa")); addProperty("translationEn", token.s("translationEn")) }
                                            }.padding(horizontal = 3.dp, vertical = 8.dp)) {
                                                Text(token.s("surface"), style = MaterialTheme.typography.titleMedium)
                                                if(mode == 2) Text(token.tr("translation", fa), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                                if(mode == 1) Text(line.tr("translation", fa))
                            }
                        }
                        if(lines.size > access.limit("lyrics")) item(key = "lyrics-preview") { PremiumPreview() }
                        item(key = "lyric-quiz") { Action(tr("آماده‌ای؟ خودت را امتحان کن", "Ready? Test what you learned"), Modifier.fillMaxWidth(), onClick = openQuiz) }
                    }
                    1 -> { items(data.a("grammar").take(access.limit("grammar")), key = { "grammar-${it.s("id")}" }, contentType = { "note" }) { note -> LearningNote(note, model::speak) { SaveAction(model, "grammar", note.s("id")) } }
                        if(data.a("grammar").size > access.limit("grammar")) item(key = "grammar-preview") { PremiumPreview() }
                    }
                    2 -> {
                        if(!access.preview) items(data.a("vocabularyTables"), contentType = { "table" }) { Panel { LearningTable(it) } }
                        items(data.a("vocabulary").take(access.limit("vocabulary")), key = { "word-${it.s("lexemeId")}" }, contentType = { "word" }) { word -> Panel {
                            Foreign(word.s("lemma"), large = true)
                            Text(word.tr("translation", fa))
                            Foreign(word.s("ipa")); if(fa) Text(word.s("faReading"))
                            Text(word.tr("part", fa), style = MaterialTheme.typography.labelMedium)
                            Foreign(word.obj("example").s("text"))
                            Text(word.obj("example").tr("translation", fa))
                            PressTextButton({ model.speak(word.s("lemma")) }) { Text(tr("تلفظ", "Pronounce")) }
                            SaveAction(model, "vocabulary", word.s("lexemeId"))
                        } }
                        if(data.a("vocabulary").size > access.limit("vocabulary") || (access.preview && data.a("vocabularyTables").isNotEmpty())) item(key = "words-preview") { PremiumPreview() }
                    }
                    3 -> listOf("idioms", "sentences").forEach { type ->
                        item(key = "$type-heading") { Text(if(type == "idioms") tr("اصطلاحات", "Expressions") else tr("جمله‌های کاربردی", "Useful sentences"), style = MaterialTheme.typography.titleLarge) }
                        items(data.a(type).take(access.limit(type)), key = { "$type-${it.s("id")}" }, contentType = { "phrase" }) { item -> Panel {
                            Foreign(item.s("text"), large = true); Text(item.tr("translation", fa)); Text(item.tr("context", fa))
                            if(item.has("example")) Foreign(item.obj("example").s("text"))
                            SaveAction(model, type, item.s("id"))
                        } }
                        if(data.a(type).size > access.limit(type)) item(key = "$type-preview") { PremiumPreview() }
                    }
                }
            }
            if(access.preview) item(key = "subscription") { SubscriptionOffer { subscriptionInfo = true } }
        }
    }
    if(subscriptionInfo) AlertDialog(
        onDismissRequest = { subscriptionInfo = false },
        title = { Text(tr("اشتراک پریمیوم", "Premium subscription")) },
        text = { Text(tr("خرید اشتراک هنوز به فروشگاه متصل نشده است؛ در این نسخه پرداختی انجام نمی‌شود.", "Subscription checkout is not connected yet. No payment is taken in this version.")) },
        confirmButton = { PressTextButton({ subscriptionInfo = false }) { Text(tr("باشه", "OK")) } }
    )
    selectedWord?.let { word -> ReedanceBottomSheet(onDismissRequest = { selectedWord = null }) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Foreign(word.s("lemma"), large = true); Foreign(word.s("ipa"))
            if(fa) Text(word.s("faReading"))
            Text(word.tr("translation", fa), style = MaterialTheme.typography.titleLarge)
            Foreign(word.obj("example").s("text")); Text(word.obj("example").tr("translation", fa))
            PressTextButton({ model.speak(word.s("lemma")) }) { Text(tr("شنیدن تلفظ", "Hear pronunciation")) }
            if(word.s("lexemeId").isNotEmpty()) SaveAction(model, "vocabulary", word.s("lexemeId"))
            Spacer(Modifier.height(24.dp))
        }
    } }
}
@Composable fun SaveAction(model: AppModel, type: String, id: String) {
    val bookmarks by model.bookmarks.collectAsStateWithLifecycle()
    val saved = bookmarks.any { it.itemType == type && it.itemId == id }
    PressTextButton({ model.toggleBookmark(type, id) }) {
        AppIcon(UiIcons.Bookmark, null, tint = if(saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if(saved) tr("نشان‌شده", "Saved") else tr("نشان کردن", "Save for later"))
    }
}
@Composable private fun PlayerPanel(model: AppModel) {
    val playing by model.playing.collectAsStateWithLifecycle()
    val position by model.position.collectAsStateWithLifecycle()
    val duration by model.duration.collectAsStateWithLifecycle()
    val repeat by model.repeat.collectAsStateWithLifecycle()
    val muted by model.muted.collectAsStateWithLifecycle()
    var dragging by remember { mutableStateOf<Float?>(null) }
    Panel {
        Foreign("Midnight Avenue", large = true)
        Text("Reedance Originals · " + tr("دموی بی‌کلام", "Instrumental demo"), style = MaterialTheme.typography.bodySmall)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column {
                Slider(value = dragging ?: position.toFloat(), onValueChange = { dragging = it }, valueRange = 0f..duration.toFloat().coerceAtLeast(1f), onValueChangeFinished = { dragging?.let { model.seek(it.toLong()) }; dragging = null })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(clock(position)); Text(clock(duration)) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    PressIconButton(model::toggleRepeat) { AppIcon(UiIcons.Repeat, tr("تکرار", "Repeat"), tint = if(repeat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    PressIconButton({ model.seek(position - 10000) }) { AppIcon(UiIcons.Replay10, tr("۱۰ ثانیه عقب", "Back 10 seconds")) }
                    PressFilledIconButton(model::togglePlay, Modifier.size(64.dp)) { AppIcon(if(playing) UiIcons.Pause else UiIcons.Play, tr("پخش یا مکث", "Play or pause"), Modifier.size(32.dp)) }
                    PressIconButton({ model.seek(position + 10000) }) { AppIcon(UiIcons.Forward10, tr("۱۰ ثانیه جلو", "Forward 10 seconds")) }
                    PressIconButton(model::toggleMute) { AppIcon(if(muted) UiIcons.Mute else UiIcons.Volume, tr("بی‌صدا", "Mute")) }
                }
            }
        }
    }
}
