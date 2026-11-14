package com.hydro.reedance.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
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
import com.hydro.reedance.domain.SongEntryState
import com.hydro.reedance.domain.songEntryState

fun clock(ms: Long): String = "%d:%02d".format(ms / 60000, ms / 1000 % 60)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun SongScreen(model: AppModel, songId: String, tab: Int, setTab: (Int) -> Unit, back: () -> Unit, quiz: () -> Unit) {
    val catalog by model.catalog.collectAsStateWithLifecycle()
    val loadedLesson by model.lesson.collectAsStateWithLifecycle()
    val lesson = loadedLesson?.takeIf { it.s("songId") == songId } ?: model.cachedLesson(songId)
    var loading by remember(songId) { mutableStateOf(true) }
    var loadError by remember(songId) { mutableStateOf(false) }
    var reload by remember(songId) { mutableIntStateOf(0) }
    val busy by model.busy.collectAsStateWithLifecycle()
    val activeLineId by model.activeLineId.collectAsStateWithLifecycle()
    val playing by model.playing.collectAsStateWithLifecycle()
    val activePlayback by model.activePlayback.collectAsStateWithLifecycle()
    val bookmarks by model.bookmarks.collectAsStateWithLifecycle()
    val song = catalog.find { it.s("id") == songId } ?: JsonObject()
    val access = LessonAccess(premium = song.s("access") == "paid")
    var subscriptionInfo by remember { mutableStateOf(false) }
    val openQuiz = { if(access.preview) subscriptionInfo = true else quiz() }
    val fa = LocalFa.current
    var mode by rememberSaveable { mutableIntStateOf(1) }
    var selectedWord by remember { mutableStateOf<JsonObject?>(null) }
    val scroll = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val collapseThreshold = with(LocalDensity.current) { 140.dp.roundToPx() }
    val compactController by remember(scroll, collapseThreshold) { derivedStateOf {
        scroll.firstVisibleItemIndex > 0 || scroll.firstVisibleItemScrollOffset > collapseThreshold
    } }
    val motionDuration = if(LocalReducedMotion.current) 0 else 180
    val available = lesson?.s("songId") == songId
    val entryState = songEntryState(available, loading, loadError)
    LaunchedEffect(songId, reload) {
        loading = true; loadError = false
        try { model.openInstalled(songId) } catch(cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch(_: Exception) { loadError = true }
        finally { loading = false }
    }
    LaunchedEffect(loadedLesson) { if(loadedLesson?.s("songId") == songId) model.preparePlayback() }
    val coverResult by produceState<Pair<String, String?>?>(null, songId, lesson) {
        value = songId to lesson?.let { model.songCoverPath(songId, it) }
    }
    val coverPath = coverResult?.takeIf { it.first == songId }?.second
    val lines = remember(lesson) { lesson?.a("lyrics").orEmpty() }
    val wordsById = remember(lesson, access) { lesson?.a("vocabulary").orEmpty().take(access.limit("vocabulary")).associateBy { it.s("lexemeId") } }
    Column(Modifier.fillMaxSize()) {
        HeaderSurface {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PressIconButton(back) { AppIcon(UiIcons.Back, tr("بازگشت", "Back")) }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        SongArtwork(songId, song.s("title"), coverPath, Modifier.size(40.dp).clip(CircleShape))
                    }
                    BookmarkButton(bookmarks.any { it.songId == songId && it.itemType == "song" && it.itemId == songId }) {
                        model.toggleBookmark("song", songId, songId)
                    }
                }
                AnimatedVisibility(available && compactController,
                    enter = fadeIn(tween(motionDuration)) + expandVertically(tween(motionDuration)),
                    exit = fadeOut(tween(motionDuration)) + shrinkVertically(tween(motionDuration))) {
                    lesson?.let { data -> CompactSongController(model, data, { scope.launch { scroll.animateScrollToItem(0) } }) }
                }
            }
        }
        LazyColumn(state = scroll, modifier = Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if(entryState == SongEntryState.ERROR) {
                item(key = "load-error") { Panel {
                    Text(tr("خواندن بسته ناموفق بود؛ نصب قبلی حفظ شده است.", "Could not read the installed package. Your installation is unchanged."))
                    Action(tr("تلاش دوباره", "Retry")) { reload++ }
                } }
            } else if(entryState == SongEntryState.LOADING) {
                item(key = "loading") { Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else if(entryState == SongEntryState.NOT_INSTALLED) {
                item(key = "install") { Panel {
                    CoverImage(if(song.s("distributionRef") == "gulum-ii") "pending-gulum-ii" else songId, song.s("title"), Modifier.fillMaxWidth().height(200.dp))
                    Heading(song.s("title"), song.s("artist"))
                    if(song.s("distributionRef").isNotEmpty()) {
                        val status by model.installProgress.collectAsStateWithLifecycle()
                        if(busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(status) }
                        val bundled = song.get("bundledPack")?.asBoolean == true
                        Action(if(bundled) tr("نصب بستهٔ همراه برنامه", "Install bundled package") else tr("دانلود و نصب بسته", "Download and install package"), enabled = !busy,
                            onClick = { if(bundled) model.installBundledPackage(songId) else model.installPackage(songId) })
                        if(bundled) Text(tr("این فایل همراه برنامه است؛ رمز فایل را در پروفایل وارد کن. تأیید امضای مستقل ناشر انجام نشده است.", "Bundled file: enter its file password in Profile. Independent publisher signature has not been verified."))
                        if(busy) PressTextButton(model::cancelInstall) { Text(tr("لغو", "Cancel")) }
                        Text(tr("رمز فایل را در پروفایل وارد کن. نصب فایل محلی هم از پروفایل انجام می‌شود؛ دانلود شبکه به manifest امضاشده نیاز دارد.", "Set the file password or import a local pack in Profile. Network downloads require a signed manifest."))
                    } else Text(tr("این عنوان فقط پیش‌نمایش است. صوت و مجوز انتشار برای آن ارائه نشده است.", "Catalog preview only. Licensed audio and a published package have not been supplied."))
                }
                }
            } else {
                val data = lesson!!
                item(key = "player", contentType = "player") { PlayerPanel(model, data) }
                item(key = "credits") { Text(data.obj("credits").tr("notice", fa), style = MaterialTheme.typography.bodySmall) }
                item(key = "tabs") { PillTabs(listOf(
                    PillOption(tr("لیریک", "Lyrics"), UiIcons.Lyrics, filledIcon = UiIcons.LyricsFilled),
                    PillOption(tr("نکته‌ها", "Notes"), UiIcons.Notes, filledIcon = UiIcons.NotesFilled),
                    PillOption(tr("لغت‌ها", "Words"), UiIcons.Words, filledIcon = UiIcons.WordsFilled),
                    PillOption(tr("عبارت‌ها", "Phrases"), UiIcons.Phrases, filledIcon = UiIcons.PhrasesFilled)
                ), tab, setTab) }
                item(key = "quiz") { PressOutlinedButton(openQuiz, Modifier.fillMaxWidth()) { AppIcon(UiIcons.Quiz, null); Text(tr("آزمون آهنگ", "Song quiz")) } }
                when(tab) {
                    0 -> {
                        item(key = "lyric-mode") { PillTabs(listOf(
                            PillOption(tr("متن اصلی", "Original"), UiIcons.Original, filledIcon = UiIcons.OriginalFilled),
                            PillOption(tr("ترجمهٔ خطی", "Line by line"), UiIcons.Lines, filledIcon = UiIcons.LinesFilled),
                            PillOption(tr("کلمه‌به‌کلمه", "Word by word"), UiIcons.WordMode, filledIcon = UiIcons.WordModeFilled)
                        ), mode) { mode = it } }
                        item(key = "lyric-help") { Text(tr("برای پخش از هر خط، روی آیکون پخش کنار آن بزن؛ برای معنی روی واژه بزن.", "Tap the play icon beside a line to play from there, or a word to explore it."), style = MaterialTheme.typography.bodySmall) }
                        items(lines.take(access.limit("lyrics")), key = { "lyric-${it.s("id")}" }, contentType = { "lyric" }) { line ->
                            val active = activeLineId == line.s("id")
                            LyricLine(line, mode, active, playing = playing && active && activePlayback?.songId == songId,
                                onPlay = {
                                    if(playing && active && activePlayback?.songId == songId) model.pause()
                                    else model.playFromLine(songId, line.n("startMs"))
                                }, onToken = { token ->
                                if(access.preview && token.s("lexemeId") !in wordsById) subscriptionInfo = true
                                else selectedWord = wordsById[token.s("lexemeId")] ?: JsonObject().apply {
                                    addProperty("lemma", token.s("surface")); addProperty("translationFa", token.s("translationFa")); addProperty("translationEn", token.s("translationEn"))
                                }
                            })
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
    val lesson by model.lesson.collectAsStateWithLifecycle()
    val saved = bookmarks.any { it.songId == lesson?.s("songId") && it.itemType == type && it.itemId == id }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        ItemBookmarkButton(saved, { model.toggleBookmark(type, id) })
    }
}
@Composable private fun PlayerPanel(model: AppModel, data: JsonObject) {
    val playing by model.playing.collectAsStateWithLifecycle()
    val position by model.position.collectAsStateWithLifecycle()
    val duration by model.duration.collectAsStateWithLifecycle()
    val repeat by model.repeat.collectAsStateWithLifecycle()
    val muted by model.muted.collectAsStateWithLifecycle()
    var dragging by remember(data.s("songId")) { mutableStateOf<Float?>(null) }
    Surface(Modifier.fillMaxWidth().itemEntrance(), shape = RoundedCornerShape(22.dp), color = Color(0xFF01020A), contentColor = Color.White) {
        Box {
            BannerGradient(animate = playing, modifier = Modifier.matchParentSize())
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Foreign(data.s("title"), large = true)
                Text(data.s("artist"), style = MaterialTheme.typography.bodySmall, color = Color.White)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column {
                        WaveSeekBar(value = dragging ?: position.toFloat(), duration = duration.toFloat(), playing = playing,
                            onValueChange = { dragging = it }, onValueChangeFinished = { dragging?.let { model.seek(it.toLong()) }; dragging = null })
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(clock(dragging?.toLong() ?: position), fontSize = 11.sp, color = Color.White)
                            Text(clock(duration), fontSize = 11.sp, color = Color.White)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            PressIconButton(model::toggleRepeat, Modifier.size(48.dp).background(if(repeat) Color.White.copy(alpha = .18f) else Color.Transparent, CircleShape)) {
                                AppIcon(UiIcons.Repeat, tr("تکرار", "Repeat"), Modifier.size(18.dp), tint = Color.White)
                            }
                            PressIconButton({ model.seek(position - 10000) }, Modifier.size(48.dp)) {
                                AppIcon(UiIcons.Replay10, tr("۱۰ ثانیه عقب", "Back 10 seconds"), Modifier.size(18.dp), tint = Color.White)
                            }
                            PressIconButton(model::togglePlay, Modifier.size(48.dp).background(Color.White.copy(alpha = .18f), CircleShape)) {
                                AppIcon(if(playing) UiIcons.PauseFilled else UiIcons.PlayFilled, tr("پخش یا مکث", "Play or pause"), Modifier.size(24.dp), tint = Color.White)
                            }
                            PressIconButton({ model.seek(position + 10000) }, Modifier.size(48.dp)) {
                                AppIcon(UiIcons.Forward10, tr("۱۰ ثانیه جلو", "Forward 10 seconds"), Modifier.size(18.dp), tint = Color.White)
                            }
                            PressIconButton(model::toggleMute, Modifier.size(48.dp)) {
                                AppIcon(if(muted) UiIcons.Mute else UiIcons.Volume, tr("بی‌صدا", "Mute"), Modifier.size(18.dp), tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Pinned compact controls appear as the full controller scrolls out of view. */
@Composable private fun CompactSongController(model: AppModel, data: JsonObject, expand: () -> Unit) {
    val playing by model.playing.collectAsStateWithLifecycle()
    val position by model.position.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), shape = RoundedCornerShape(18.dp),
        color = Color(0xFF0B0B0B), contentColor = Color.White) {
        Box {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable(onClick = expand).padding(horizontal = 8.dp)) {
                    Text(data.s("title"), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    Text(clock(position), fontSize = 11.sp, color = Color.White)
                }
                PressIconButton({ model.seek(position - 10000) }) {
                    AppIcon(UiIcons.Replay10, tr("۱۰ ثانیه عقب", "Back 10 seconds"), Modifier.size(18.dp), tint = Color.White)
                }
                PressIconButton(model::togglePlay) {
                    AppIcon(if(playing) UiIcons.PauseFilled else UiIcons.PlayFilled, tr("پخش یا مکث", "Play or pause"), Modifier.size(20.dp), tint = Color.White)
                }
            }
        }
    }
}
