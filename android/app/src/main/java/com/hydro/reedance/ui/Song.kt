package com.hydro.reedance.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
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

fun clock(ms: Long): String = "%d:%02d".format(ms / 60000, ms / 1000 % 60)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun SongScreen(model: AppModel, songId: String, tab: Int, setTab: (Int) -> Unit, back: () -> Unit, quiz: () -> Unit) {
    val catalog by model.catalog.collectAsStateWithLifecycle()
    val lesson by model.lesson.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val position by model.position.collectAsStateWithLifecycle()
    val playing by model.playing.collectAsStateWithLifecycle()
    val bookmarks by model.bookmarks.collectAsStateWithLifecycle()
    val song = catalog.find { it.s("id") == songId } ?: JsonObject()
    val fa = LocalFa.current
    var mode by rememberSaveable { mutableIntStateOf(1) }
    var selectedWord by remember { mutableStateOf<JsonObject?>(null) }
    val scroll = rememberScrollState()
    val collapsed by remember { derivedStateOf { scroll.value > 300 } }
    val available = songId == "midnight-avenue" && lesson != null
    Column(Modifier.fillMaxSize()) {
        Surface(shadowElevation = if(collapsed) 4.dp else 0.dp) {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("بازگشت", "Back")) }
                    Column(Modifier.weight(1f)) {
                        Text(song.s("title"), style = MaterialTheme.typography.titleMedium)
                        Text("${song.s("language").uppercase()} · ${song.s("level")} · ${song.s("duration")}", style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton({ model.toggleBookmark("song", songId) }) {
                        Icon(if(bookmarks.any { it.itemType == "song" && it.itemId == songId }) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, tr("ذخیره", "Save"))
                    }
                }
                AnimatedVisibility(collapsed && available) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(song.s("title"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        IconButton(model::togglePlay) { Icon(if(playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, tr("پخش یا مکث", "Play or pause")) }
                    }
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if(!available) {
                Panel {
                    Image(painterResource(cover(songId)), song.s("title"), Modifier.fillMaxWidth().height(200.dp))
                    Heading(song.s("title"), song.s("artist"))
                    if(songId == "midnight-avenue" && BuildConfig.DEBUG) {
                        Text(tr("صوت و تمام آموزش‌ها را یک‌بار در فضای خصوصی برنامه نصب کن؛ سپس آفلاین تمرین کن.", "Install the audio and lessons into private storage once, then practice offline."))
                        Text(tr("نسخهٔ آزمایشی: این دمو همراه APK است؛ دانلود شبکه یا بستهٔ امضاشده نیست.", "Debug demo: bundled with the APK, not a network download or a signed production pack."), style = MaterialTheme.typography.bodySmall)
                        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Action(tr("نصب و باز کردن دموی محلی", "Install and open local demo"), enabled = !busy, onClick = model::installDemo)
                    } else Text(tr("این عنوان فقط پیش‌نمایش است. صوت و مجوز انتشار برای آن ارائه نشده است.", "Catalog preview only. Licensed audio and a published package have not been supplied."))
                }
            } else {
                val data = lesson!!
                PlayerPanel(model)
                Text(data.obj("credits").tr("notice", fa), style = MaterialTheme.typography.bodySmall)
                ChoiceRow(listOf(tr("لیریک", "Lyrics"), tr("نکته‌ها", "Notes"), tr("لغت‌ها", "Words"), tr("عبارت‌ها", "Phrases")), tab, setTab)
                OutlinedButton(quiz, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Quiz, null); Text(tr("آزمون آهنگ", "Song quiz")) }
                when(tab) {
                    0 -> {
                        ChoiceRow(listOf(tr("متن اصلی", "Original"), tr("ترجمهٔ خطی", "Line by line"), tr("کلمه‌به‌کلمه", "Word by word")), mode) { mode = it }
                        Text(tr("روی زمان برای جابه‌جایی و روی واژه برای معنی بزن.", "Tap a timestamp to seek, or a word to explore it."), style = MaterialTheme.typography.bodySmall)
                        data.a("lyrics").forEachIndexed { i, line ->
                            val next = data.a("lyrics").getOrNull(i+1)?.n("startMs") ?: 64000
                            val active = position >= line.n("startMs") && position < next
                            Panel(tone = if(active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
                                TextButton({ model.seek(line.n("startMs")) }) { Text(clock(line.n("startMs"))) }
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        line.a("tokens").forEach { token ->
                                            Column(Modifier.clickable {
                                                selectedWord = data.a("vocabulary").find { it.s("lexemeId") == token.s("lexemeId") } ?: JsonObject().apply { addProperty("lemma", token.s("surface")); addProperty("translationFa", token.s("translationFa")); addProperty("translationEn", token.s("translationEn")) }
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
                        Action(tr("آماده‌ای؟ خودت را امتحان کن", "Ready? Test what you learned"), Modifier.fillMaxWidth(), onClick = quiz)
                    }
                    1 -> data.a("grammar").forEach { note -> LearningNote(note, model::speak) { SaveAction(model, "grammar", note.s("id")) } }
                    2 -> {
                        data.a("vocabularyTables").forEach { Panel { LearningTable(it) } }
                        data.a("vocabulary").forEach { word -> Panel {
                            Foreign(word.s("lemma"), large = true)
                            Text(word.tr("translation", fa))
                            Foreign(word.s("ipa")); if(fa) Text(word.s("faReading"))
                            Text(word.tr("part", fa), style = MaterialTheme.typography.labelMedium)
                            Foreign(word.obj("example").s("text"))
                            Text(word.obj("example").tr("translation", fa))
                            TextButton({ model.speak(word.s("lemma")) }) { Text(tr("تلفظ", "Pronounce")) }
                            SaveAction(model, "vocabulary", word.s("lexemeId"))
                        } }
                    }
                    3 -> listOf("idioms", "sentences").forEach { type ->
                        Text(if(type == "idioms") tr("اصطلاحات", "Expressions") else tr("جمله‌های کاربردی", "Useful sentences"), style = MaterialTheme.typography.titleLarge)
                        data.a(type).forEach { item -> Panel {
                            Foreign(item.s("text"), large = true); Text(item.tr("translation", fa)); Text(item.tr("context", fa))
                            if(item.has("example")) Foreign(item.obj("example").s("text"))
                            SaveAction(model, type, item.s("id"))
                        } }
                    }
                }
            }
        }
    }
    selectedWord?.let { word -> ModalBottomSheet(onDismissRequest = { selectedWord = null }) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Foreign(word.s("lemma"), large = true); Foreign(word.s("ipa"))
            if(fa) Text(word.s("faReading"))
            Text(word.tr("translation", fa), style = MaterialTheme.typography.titleLarge)
            Foreign(word.obj("example").s("text")); Text(word.obj("example").tr("translation", fa))
            TextButton({ model.speak(word.s("lemma")) }) { Text(tr("شنیدن تلفظ", "Hear pronunciation")) }
            if(word.s("lexemeId").isNotEmpty()) SaveAction(model, "vocabulary", word.s("lexemeId"))
            Spacer(Modifier.height(24.dp))
        }
    } }
}
@Composable fun SaveAction(model: AppModel, type: String, id: String) {
    val bookmarks by model.bookmarks.collectAsStateWithLifecycle()
    val saved = bookmarks.any { it.itemType == type && it.itemId == id }
    TextButton({ model.toggleBookmark(type, id) }) {
        Icon(if(saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, null)
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
                    IconButton(model::toggleRepeat) { Icon(Icons.Rounded.Repeat, tr("تکرار", "Repeat"), tint = if(repeat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton({ model.seek(position - 10000) }) { Icon(Icons.Rounded.Replay10, tr("۱۰ ثانیه عقب", "Back 10 seconds")) }
                    FilledIconButton(model::togglePlay, Modifier.size(64.dp)) { Icon(if(playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, tr("پخش یا مکث", "Play or pause"), Modifier.size(32.dp)) }
                    IconButton({ model.seek(position + 10000) }) { Icon(Icons.Rounded.Forward10, tr("۱۰ ثانیه جلو", "Forward 10 seconds")) }
                    IconButton(model::toggleMute) { Icon(if(muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, tr("بی‌صدا", "Mute")) }
                }
            }
        }
    }
}
