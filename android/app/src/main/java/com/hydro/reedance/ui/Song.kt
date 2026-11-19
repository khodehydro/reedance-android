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
import androidx.compose.ui.draw.blur
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.JsonObject
import com.hydro.reedance.data.*
import com.hydro.reedance.domain.LessonAccess
import com.hydro.reedance.domain.PremiumEntitlement
import com.hydro.reedance.domain.SongEntryState
import com.hydro.reedance.domain.songEntryState

fun clock(ms: Long): String = "%d:%02d".format(ms / 60000, ms / 1000 % 60)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun SongPlaybackScreen(model: AppModel, songId: String, tab: Int, setTab: (Int) -> Unit, back: () -> Unit, download: () -> Unit, quiz: () -> Unit, story: () -> Unit, premium: () -> Unit) {
    val catalog by model.catalog.collectAsStateWithLifecycle()
    val loadedLesson by model.lesson.collectAsStateWithLifecycle()
    val lesson = loadedLesson?.takeIf { it.s("songId") == songId } ?: model.cachedLesson(songId)
    var loading by remember(songId) { mutableStateOf(true) }
    var loadError by remember(songId) { mutableStateOf(false) }
    var reload by remember(songId) { mutableIntStateOf(0) }
    val activeLineId by model.activeLineId.collectAsStateWithLifecycle()
    val playing by model.playing.collectAsStateWithLifecycle()
    val activePlayback by model.activePlayback.collectAsStateWithLifecycle()
    val bookmarks by model.bookmarks.collectAsStateWithLifecycle()
    val subscribed by PremiumEntitlement.active.collectAsStateWithLifecycle()
    val song = catalog.find { it.s("id") == songId } ?: JsonObject()
    val access = LessonAccess(premium = song.s("access") == "paid", subscribed = subscribed)
    var subscriptionInfo by remember { mutableStateOf(false) }
    val openQuiz = { if(access.preview) subscriptionInfo = true else quiz() }
    var mode by rememberSaveable(songId) { mutableIntStateOf(0) }
    var singleLyric by rememberSaveable(songId) { mutableStateOf(false) }
    var selectedWord by remember { mutableStateOf<JsonObject?>(null) }
    val scroll = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val collapseThreshold = with(LocalDensity.current) { 140.dp.roundToPx() }
    val compactController by remember(scroll, collapseThreshold) { derivedStateOf {
        scroll.firstVisibleItemIndex > 0 || scroll.firstVisibleItemScrollOffset > collapseThreshold
    } }
    val motionDuration = if(LocalReducedMotion.current) 0 else 180
    val hasStory = remember(lesson, songId) { lessonStory(lesson, songId) != null }
    val openStory = if(!access.preview && hasStory) story else null
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
                    Column {
                        lesson?.let { data -> CompactSongController(model, data, { scope.launch { scroll.animateScrollToItem(0) } }) }
                        Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp)) {
                            LessonTabs(tab, { next ->
                                if(next != tab) {
                                    setTab(next)
                                    scroll.requestScrollToItem(2) // The first lesson item, after player and tab slots.
                                }
                            }, mode, { mode = it }, singleLyric, { singleLyric = !singleLyric }, openQuiz, openStory, compact = true)
                        }
                    }
                }
            }
        }
        LazyColumn(state = scroll, modifier = Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if(entryState == SongEntryState.ERROR) {
                item(key = "load-error") { Panel {
                    Text(tr("خواندن آهنگ ناموفق بود؛ فایل قبلی تغییری نکرده است.", "Could not read the song. The existing download is unchanged."))
                    Action(tr("تلاش دوباره", "Retry")) { reload++ }
                } }
            } else if(entryState == SongEntryState.LOADING) {
                item(key = "loading") { Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else if(entryState == SongEntryState.NOT_INSTALLED) {
                item(key = "song-not-downloaded") { Panel {
                    Text(tr("این آهنگ هنوز دانلود نشده است.", "This song hasn’t been downloaded yet."))
                    Action(tr("رفتن به دانلود آهنگ", "Go to song download"), onClick = download)
                } }
            } else {
                val data = lesson!!
                item(key = "player", contentType = "player") { PlayerPanel(model, data) }
                item(key = "tabs") {
                    if(!compactController) LessonTabs(tab, setTab, mode, { mode = it }, singleLyric, { singleLyric = !singleLyric }, openQuiz, openStory)
                }
                when(tab) {
                    0 -> {
                        item(key = "lyric-help") { Text(tr("برای پخش از هر خط، روی آیکون پخش کنار آن بزن؛ برای معنی روی واژه بزن.", "Tap the play icon beside a line to play from there, or a word to explore it."), style = MaterialTheme.typography.bodySmall) }
                        val visibleLines = lines.take(access.limit("lyrics"))
                        val renderLine: @Composable (JsonObject) -> Unit = { line ->
                            val active = activeLineId == line.s("id")
                            LyricLine(line, mode, active, single = singleLyric, playing = playing && active && activePlayback?.songId == songId,
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
                        if(singleLyric) {
                            item(key = "single-lyric", contentType = "single-lyric") {
                                TimedLyricBox(model, songId, visibleLines, renderLine)
                            }
                            items(lines.drop(access.limit("lyrics")), key = { "locked-lyric-${it.s("id")}" }, contentType = { "lyric" }) { line ->
                                LockedLessonItem(onUnlock = { subscriptionInfo = true }) { renderLine(line) }
                            }
                        } else {
                            itemsIndexed(lines, key = { _, line -> "lyric-${line.s("id")}" }, contentType = { _, _ -> "lyric" }) { index, line ->
                                if(index < access.limit("lyrics")) renderLine(line)
                                else LockedLessonItem(onUnlock = { subscriptionInfo = true }) { renderLine(line) }
                            }
                        }
                        if(lines.size > access.limit("lyrics")) item(key = "lyrics-preview") { PremiumPreview() }
                    }
                    1 -> { itemsIndexed(data.a("grammar"), key = { _, it -> "grammar-${it.s("id")}" }, contentType = { _, _ -> "note" }) { index, note ->
                            val content: @Composable () -> Unit = { LearningNote(note) { SaveAction(model, "grammar", note.s("id")) } }
                            if(index < access.limit("grammar")) content() else LockedLessonItem(onUnlock = { subscriptionInfo = true }) { content() }
                        }
                        if(data.a("grammar").size > access.limit("grammar")) item(key = "grammar-preview") { PremiumPreview() }
                    }
                    2 -> {
                        if(!access.preview) items(data.a("vocabularyTables"), contentType = { "table" }) { Panel { LearningTable(it) } }
                        itemsIndexed(data.a("vocabulary"), key = { _, it -> "word-${it.s("lexemeId")}" }, contentType = { _, _ -> "word" }) { index, word ->
                            val content: @Composable () -> Unit = { Panel {
                                WordLearningContent(word)
                                SaveAction(model, "vocabulary", word.s("lexemeId"))
                            } }
                            if(index < access.limit("vocabulary")) content() else LockedLessonItem(onUnlock = { subscriptionInfo = true }) { content() }
                        }
                        if(data.a("vocabulary").size > access.limit("vocabulary") || (access.preview && data.a("vocabularyTables").isNotEmpty())) item(key = "words-preview") { PremiumPreview() }
                    }
                    3 -> listOf("idioms", "sentences").forEach { type ->
                        item(key = "$type-heading") { Text(if(type == "idioms") tr("اصطلاحات", "Expressions") else tr("جمله‌های کاربردی", "Useful sentences"), style = MaterialTheme.typography.titleLarge) }
                        itemsIndexed(data.a(type), key = { _, it -> "$type-${it.s("id")}" }, contentType = { _, _ -> "phrase" }) { index, item ->
                            val content: @Composable () -> Unit = { Panel {
                                PhraseLearningContent(item)
                                SaveAction(model, type, item.s("id"))
                            } }
                            if(index < access.limit(type)) content() else LockedLessonItem(onUnlock = { subscriptionInfo = true }) { content() }
                        }
                        if(data.a(type).size > access.limit(type)) item(key = "$type-preview") { PremiumPreview() }
                    }
                }
                if(!subscribed) item(key = "subscription") { ProfileMembershipCard(premium, clickableCard = true) }
            }
        }
    }
    if(subscriptionInfo) AlertDialog(
        onDismissRequest = { subscriptionInfo = false },
        title = { Text(tr("اشتراک پریمیوم", "Premium subscription")) },
        text = { Text(tr("خرید اشتراک هنوز به فروشگاه متصل نشده است؛ در این نسخه پرداختی انجام نمی‌شود.", "Subscription checkout is not connected yet. No payment is taken in this version.")) },
        confirmButton = { PressTextButton({ subscriptionInfo = false; premium() }) { Text(tr("دیدن امکانات پریمیوم", "Explore Premium")) } },
        dismissButton = { PressTextButton({ subscriptionInfo = false }) { Text(tr("بعداً", "Later")) } }
    )
    selectedWord?.let { word -> ReedanceBottomSheet(onDismissRequest = { selectedWord = null }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            WordLearningContent(word)
            if(word.s("lexemeId").isNotEmpty()) SaveAction(model, "vocabulary", word.s("lexemeId"))
            Spacer(Modifier.height(24.dp))
        }
    } }
}
@Composable private fun LockedLessonItem(onUnlock: () -> Unit, content: @Composable () -> Unit) {
    val hasNativeBlur = android.os.Build.VERSION.SDK_INT >= 31
    Box {
        Box(Modifier.clearAndSetSemantics { }.then(if(hasNativeBlur) Modifier.blur(8.dp) else Modifier)) { content() }
        Box(Modifier.matchParentSize().background(Color.White.copy(alpha = if(hasNativeBlur) .38f else .94f)).clickable(onClick = onUnlock), contentAlignment = Alignment.Center) {
            Text(tr("ادامه با اشتراک", "Unlock with Premium"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
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
        color = MaterialTheme.colorScheme.surfaceContainerLowest, contentColor = MaterialTheme.colorScheme.onSurface) {
        Box {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable(onClick = expand).padding(horizontal = 8.dp)) {
                    Text(data.s("title"), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                    Text(clock(position), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                PressIconButton({ model.seek(position - 10000) }) {
                    AppIcon(UiIcons.Replay10, tr("۱۰ ثانیه عقب", "Back 10 seconds"), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                }
                PressIconButton(model::togglePlay) {
                    AppIcon(if(playing) UiIcons.PauseFilled else UiIcons.PlayFilled, tr("پخش یا مکث", "Play or pause"), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
