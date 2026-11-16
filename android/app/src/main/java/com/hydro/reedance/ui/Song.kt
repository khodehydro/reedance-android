package com.hydro.reedance.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

fun clock(ms: Long): String = "%d:%02d".format(ms / 60000, ms / 1000 % 60)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun SongScreen(model: AppModel, songId: String, tab: Int, setTab: (Int) -> Unit, back: () -> Unit, quiz: () -> Unit, premium: () -> Unit) {
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
    val fa = LearningLanguage.persian
    var mode by rememberSaveable { mutableIntStateOf(1) }
    var singleLyric by rememberSaveable(songId) { mutableStateOf(false) }
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
                item(key = "download-details") {
                    val bundled = song.get("bundledPack")?.asBoolean == true
                    SongDownloadDetails(
                        model = model,
                        song = song,
                        songId = songId,
                        busy = busy,
                        available = song.s("distributionRef").isNotEmpty() || bundled,
                        bundled = bundled,
                        onDownload = { if(bundled) model.installBundledPackage(songId) else model.installPackage(songId) },
                        onCancel = model::cancelInstall
                    )
                }
            } else {
                val data = lesson!!
                item(key = "player", contentType = "player") { PlayerPanel(model, data) }
                item(key = "credits") { Text(data.obj("credits").tr("notice", fa), style = MaterialTheme.typography.bodySmall) }
                item(key = "tabs") {
                    LessonTabs(tab, setTab, mode, { mode = it }, singleLyric, { singleLyric = !singleLyric }, openQuiz)
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
                        } else {
                            items(lines.take(access.limit("lyrics")), key = { "lyric-${it.s("id")}" }, contentType = { "lyric" }) { line -> renderLine(line) }
                        }
                        if(lines.size > access.limit("lyrics")) item(key = "lyrics-preview") { PremiumPreview() }
                        item(key = "lyric-quiz") { SongQuizInvitation(openQuiz) }
                    }
                    1 -> { items(data.a("grammar").take(access.limit("grammar")), key = { "grammar-${it.s("id")}" }, contentType = { "note" }) { note -> LearningNote(note) { SaveAction(model, "grammar", note.s("id")) } }
                        if(data.a("grammar").size > access.limit("grammar")) item(key = "grammar-preview") { PremiumPreview() }
                    }
                    2 -> {
                        if(!access.preview) items(data.a("vocabularyTables"), contentType = { "table" }) { Panel { LearningTable(it) } }
                        items(data.a("vocabulary").take(access.limit("vocabulary")), key = { "word-${it.s("lexemeId")}" }, contentType = { "word" }) { word -> Panel {
                            WordLearningContent(word)
                            SaveAction(model, "vocabulary", word.s("lexemeId"))
                        } }
                        if(data.a("vocabulary").size > access.limit("vocabulary") || (access.preview && data.a("vocabularyTables").isNotEmpty())) item(key = "words-preview") { PremiumPreview() }
                    }
                    3 -> listOf("idioms", "sentences").forEach { type ->
                        item(key = "$type-heading") { Text(if(type == "idioms") tr("اصطلاحات", "Expressions") else tr("جمله‌های کاربردی", "Useful sentences"), style = MaterialTheme.typography.titleLarge) }
                        items(data.a(type).take(access.limit(type)), key = { "$type-${it.s("id")}" }, contentType = { "phrase" }) { item -> Panel {
                            PhraseLearningContent(item)
                            SaveAction(model, type, item.s("id"))
                        } }
                        if(data.a(type).size > access.limit(type)) item(key = "$type-preview") { PremiumPreview() }
                    }
                }
            }
            if(access.preview) item(key = "subscription") { SubscriptionOffer(premium) }
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
@Composable private fun SongDownloadDetails(
    model: AppModel,
    song: JsonObject,
    songId: String,
    busy: Boolean,
    available: Boolean,
    bundled: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit
) {
    val transfer by model.installTransfer.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val reduced = LocalReducedMotion.current
    val coverMorph by animateFloatAsState(
        targetValue = if(busy) 1f else 0f,
        animationSpec = tween(if(reduced) 0 else 420),
        label = "download-cover-morph"
    )
    val transferFraction = transfer?.takeIf { it.phase in listOf("download", "copy") }?.fraction
    val downloadProgress = transfer?.takeIf { it.phase == "download" }?.fraction
    val packageSize = song.n("packageSizeBytes")
    val countItems = listOf(
        Triple(UiIcons.WordsFilled, tr("لغت", "Words"), "vocabularyCount"),
        Triple(UiIcons.NotesFilled, tr("نکته", "Notes"), "grammarCount"),
        Triple(UiIcons.PhrasesFilled, tr("اصطلاح", "Expressions"), "idiomCount"),
        Triple(UiIcons.LinesFilled, tr("جمله", "Sentences"), "sentenceCount")
    )
    val hasMissingCounts = countItems.any { !song.has(it.third) }
    val phaseText = when(transfer?.phase) {
        "manifest" -> tr("در حال بررسی مشخصات بسته", "Checking package details")
        "download" -> tr("در حال دانلود فایل", "Downloading package")
        "copy" -> tr("در حال آماده‌سازی فایل", "Preparing package")
        "decrypt" -> tr("در حال بازکردن بسته", "Preparing package")
        "validate" -> tr("در حال بررسی محتوا", "Validating content")
        "installed" -> tr("نصب کامل شد", "Installation complete")
        else -> tr("در حال آماده‌سازی", "Preparing")
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(tr("مشخصات آهنگ", "SONG DETAILS"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Box(Modifier.size(228.dp), contentAlignment = Alignment.Center) {
            if(busy) CircularWaveProgress(transferFraction, Modifier.fillMaxSize())
            CoverImage(
                if(song.s("distributionRef") == "gulum-ii") "pending-gulum-ii" else songId,
                song.s("title"),
                Modifier.size(184.dp),
                shapeProgress = coverMorph
            )
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(song.s("title").ifEmpty { songId }, style = MaterialTheme.typography.headlineSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(song.s("artist"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if(busy) {
                Text(
                    downloadProgress?.let { "${(it * 100f).roundToInt()}% · $phaseText" } ?: phaseText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if(song.s("language").isNotBlank()) DownloadMetaChip(tr("زبان", "Language"), languageDisplayName(song.s("language")), Modifier.weight(1f))
            if(song.s("level").isNotBlank()) DownloadMetaChip(tr("سطح", "Level"), song.s("level"), Modifier.weight(1f))
            if(song.s("duration").isNotBlank()) DownloadMetaChip(tr("زمان", "Duration"), song.s("duration"), Modifier.weight(1f))
        }

        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .78f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(UiIcons.Check, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(tr("حجم کل بسته", "Total package size"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(
                    if(packageSize > 0) android.text.format.Formatter.formatFileSize(context, packageSize)
                    else tr("هنوز مشخص نیست", "Not available yet"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("محتوای این آهنگ", "Included learning content"), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                countItems.forEach { (icon, label, key) ->
                    val count = if(song.has(key)) song.n(key).toString() else "—"
                    Surface(
                        Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = .72f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                    ) {
                        Column(Modifier.padding(vertical = 12.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AppIcon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(count, style = MaterialTheme.typography.titleMedium)
                            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if(hasMissingCounts) Text(
                tr("تعداد دقیق محتوا تا زمانی که ناشر آمار معتبر بسته را ارائه نکند نمایش داده نمی‌شود.", "Exact counts will appear when verified package metadata is available."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if(available && busy) {
            PressOutlinedButton(onCancel, Modifier.fillMaxWidth()) { Text(tr("لغو دریافت", "Cancel download")) }
        } else if(available) {
            Action(
                if(bundled) tr("نصب بستهٔ همراه برنامه", "Install bundled package") else tr("دانلود و نصب آهنگ", "Download and install song"),
                Modifier.fillMaxWidth()
            ) { onDownload() }
        } else {
            Action(tr("این آهنگ هنوز آمادهٔ دریافت نیست", "This song isn’t available yet"), Modifier.fillMaxWidth(), enabled = false) {}
            Text(tr("برای این پیش‌نمایش هنوز فایل صوتی و بستهٔ آموزشی منتشر نشده است.", "Audio and a learning package have not been published for this preview yet."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(bundled) Text(
            tr("این بسته همراه برنامه است، نه دانلود اینترنتی. برای نصب آن ابتدا رمز فایل را در پروفایل وارد کن؛ امضای مستقل ناشر تأیید نشده است.", "This package is bundled with the app, not downloaded from the internet. Enter its file password in Profile before installing; an independent publisher signature has not been verified."),
            Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable private fun DownloadMetaChip(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .68f)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable private fun CircularWaveProgress(progress: Float?, modifier: Modifier = Modifier) {
    val motion = rememberInfiniteTransition(label = "download-ring-wave")
    val phase by motion.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "download-ring-phase"
    )
    val active = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f)
    Canvas(modifier) {
        val stroke = 4.dp.toPx()
        val radius = minOf(size.width, size.height) / 2f - stroke
        drawCircle(track, radius, style = Stroke(stroke))
        val sweep = if(progress == null) 94f else 360f * progress.coerceIn(0f, 1f)
        val start = if(progress == null) -90f + phase * 180f / PI.toFloat() else -90f
        val steps = (sweep / 2f).roundToInt().coerceAtLeast(2)
        val waveAmplitude = 1.25.dp.toPx()
        val path = Path()
        repeat(steps + 1) { index ->
            val fraction = index.toFloat() / steps
            val angle = (start + sweep * fraction) * PI.toFloat() / 180f
            val radiusOffset = sin((fraction * 12f * PI.toFloat() - phase).toDouble()).toFloat() * waveAmplitude
            val r = radius + radiusOffset
            val point = Offset(
                size.width / 2f + cos(angle.toDouble()).toFloat() * r,
                size.height / 2f + sin(angle.toDouble()).toFloat() * r
            )
            if(index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        if(progress == null || progress > 0f) drawPath(path, active, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable private fun languageDisplayName(code: String) = when(code) {
    "es" -> tr("اسپانیایی", "Spanish")
    "ko" -> tr("کره‌ای", "Korean")
    "tr" -> tr("ترکی", "Turkish")
    else -> tr("انگلیسی", "English")
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
