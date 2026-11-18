package com.hydro.reedance.ui

import android.app.Activity
import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.JsonObject
import com.hydro.reedance.data.AppModel
import com.hydro.reedance.data.s
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import kotlin.math.abs

internal data class LessonStory(val title: String, val locale: String, val pages: List<String>)

private val sampleStory = LessonStory("Bir fincan çay", "tr-TR", listOf(
    "Deniz akşam eve döndü. Yağmur yağıyordu ve sokak sessizdi.",
    "Pencerenin yanında oturdu ve eski bir şarkı açtı. Şarkıyı dinlerken bir arkadaşını hatırladı.",
    "Arkadaşına bir mesaj yazdı: ‘Yarın bir çay içelim mi?’ Cevap hemen geldi: ‘Olur, seni özledim.’"
))

internal fun lessonStory(lesson: JsonObject?, songId: String): LessonStory? {
    if(lesson == null || lesson.s("songId") != songId) return null
    val story = lesson.get("story")?.takeIf { it.isJsonObject }?.asJsonObject
    val title = story?.s("title").orEmpty()
    val pages = story?.get("pages")?.takeIf { it.isJsonArray }?.asJsonArray
        ?.mapNotNull { it.takeIf { item -> item.isJsonPrimitive && item.asJsonPrimitive.isString }?.asString }
        ?.filter { it.isNotBlank() }.orEmpty()
    val locale = lesson.s("locale").ifBlank { lesson.s("targetLanguage") }
    return if(title.isNotBlank() && pages.isNotEmpty() && locale.isNotBlank()) LessonStory(title, locale, pages)
    else sampleStory.takeIf { songId == "gulum-2" }
}

@Composable internal fun SongStoryScreen(model: AppModel, songId: String, back: () -> Unit) {
    val loaded by model.lesson.collectAsStateWithLifecycle()
    val lesson = loaded?.takeIf { it.s("songId") == songId } ?: model.cachedLesson(songId)
    val story = remember(lesson, songId) { lessonStory(lesson, songId) }
    LaunchedEffect(songId) {
        if(lesson == null) try { model.openInstalled(songId) }
        catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { /* Keep the unavailable state visible. */ }
    }
    if(story == null) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            PressIconButton(back) { AppIcon(UiIcons.Back, tr("بازگشت", "Back")) }
            Text(tr("داستان در دسترس نیست.", "Story unavailable."))
        }
        return
    }
    key(songId, story) { StoryReader(story, back) }
}

@Composable private fun StoryReader(story: LessonStory, back: () -> Unit) {
    var paperIndex by rememberSaveable { mutableIntStateOf(0) }
    var fontIndex by rememberSaveable { mutableIntStateOf(1) }
    var speedIndex by rememberSaveable { mutableIntStateOf(1) }
    val speeds = listOf(.75f, 1f, 1.25f, 1.5f)
    val speed by rememberUpdatedState(speeds[speedIndex])
    val papers = listOf(Color.White, Color(0xFFF3E6C8), Color(0xFF292B30))
    val ink = if(paperIndex == 2) Color.White else Color(0xFF252322)
    val nextPaper = (paperIndex + 1) % papers.size
    val paperNames = listOf(tr("سفید", "White"), tr("کاهی", "Sepia"), tr("خاکستری تیره", "Dark gray"))
    val pager = rememberPagerState { story.pages.size }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val reduced = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val window = (context as? Activity)?.window
    DisposableEffect(window) {
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val originalBehavior = controller?.systemBarsBehavior
        val bars = WindowInsetsCompat.Type.systemBars()
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(bars)
        onDispose {
            controller?.show(bars)
            if(originalBehavior != null) controller?.systemBarsBehavior = originalBehavior
        }
    }
    val page = pager.settledPage
    var request by remember(page) { mutableStateOf(false) }
    var player by remember(page) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(page) { mutableStateOf(false) }
    var failed by remember(page) { mutableStateOf(false) }
    val scrolling by rememberUpdatedState(pager.isScrollInProgress)
    fun pause() {
        val current = player
        if(current == null) request = false else if(playing) current.pause()
        playing = false
    }
    fun play(current: MediaPlayer) {
        try {
            current.playbackParams = PlaybackParams().allowDefaults().setSpeed(speed)
            current.start()
            playing = true
        } catch(_: RuntimeException) {
            failed = true
            request = false
            playing = false
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { pause() }
    LaunchedEffect(pager.isScrollInProgress) { if(pager.isScrollInProgress) pause() }
    LaunchedEffect(speedIndex) { if(playing) player?.let(::play) }
    LaunchedEffect(story, page, request) {
        if(!request) return@LaunchedEffect
        var output: File? = null
        var audio: MediaPlayer? = null
        try {
            val file = File.createTempFile("story-", ".wav", context.cacheDir).also { output = it }
            check(synthesizeStoryPage(context, story.locale, story.pages[page], file))
            val prepared = MediaPlayer().also { audio = it }
            prepared.setDataSource(file.absolutePath)
            val ready = CompletableDeferred<Boolean>()
            prepared.setOnPreparedListener { ready.complete(true) }
            prepared.setOnErrorListener { _, _, _ ->
                ready.complete(false)
                failed = true
                request = false
                true
            }
            prepared.prepareAsync()
            check(withTimeoutOrNull(8_000) { ready.await() } == true)
            prepared.setOnCompletionListener { playing = false }
            player = prepared
            if(!scrolling) play(prepared)
            awaitCancellation()
        } catch(cancelled: CancellationException) {
            throw cancelled
        } catch(_: Exception) {
            failed = true
            request = false
        } finally {
            player = null
            playing = false
            audio?.release()
            output?.delete()
        }
    }

    Surface(color = papers[paperIndex], contentColor = ink, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                Text(tr("داستان آهنگ", "Song story"), style = MaterialTheme.typography.titleMedium)
                PressIconButton(back, Modifier.align(Alignment.CenterStart)) { AppIcon(UiIcons.Back, tr("بازگشت", "Back")) }
            }
            HorizontalPager(pager, modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black.copy(alpha = .12f))) { index ->
                val paper = papers[paperIndex]
                val pageTurn = Modifier.drawWithContent {
                    val offset = (pager.currentPage - index + pager.currentPageOffsetFraction).coerceIn(-1f, 1f)
                    val amount = abs(offset)
                    if(reduced || amount < .001f || amount >= 1f) {
                        drawContent()
                    } else {
                        val right = if(rtl) offset < 0f else offset > 0f
                        val edge = if(right) size.width else 0f
                        val direction = if(right) -1f else 1f
                        val corner = Offset(edge, size.height)
                        val creaseBottom = Offset(edge + direction * size.width * amount * .95f, size.height)
                        val creaseSide = Offset(edge, size.height * (1f - amount * .80f))
                        val foldTip = Offset(creaseBottom.x + direction * size.width * amount * .13f, creaseSide.y)
                        val curve = Offset((creaseSide.x + creaseBottom.x) / 2f, (creaseSide.y + creaseBottom.y) / 2f)
                        val face = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            if(right) {
                                lineTo(creaseSide.x, creaseSide.y)
                                quadraticTo(curve.x, curve.y + size.height * amount * .1f, creaseBottom.x, creaseBottom.y)
                                lineTo(0f, size.height)
                            } else {
                                lineTo(size.width, size.height)
                                lineTo(creaseBottom.x, creaseBottom.y)
                                quadraticTo(curve.x, curve.y + size.height * amount * .1f, creaseSide.x, creaseSide.y)
                            }
                            close()
                        }
                        clipPath(face) { this@drawWithContent.drawContent() }
                        val fold = Path().apply {
                            moveTo(creaseSide.x, creaseSide.y)
                            quadraticTo(curve.x, curve.y + size.height * amount * .1f, creaseBottom.x, creaseBottom.y)
                            quadraticTo(foldTip.x, (foldTip.y + corner.y) / 2f, foldTip.x, foldTip.y)
                            quadraticTo(curve.x, creaseSide.y, creaseSide.x, creaseSide.y)
                            close()
                        }
                        drawPath(fold, Brush.linearGradient(
                            listOf(Color.Black.copy(alpha = .52f), paper, ink.copy(alpha = .55f)),
                            start = corner, end = foldTip
                        ))
                    }
                }.graphicsLayer {
                    // Keep a small perspective tilt while the diagonal corner folds over.
                    val offset = (pager.currentPage - index + pager.currentPageOffsetFraction).coerceIn(-1f, 1f)
                    if(!reduced) {
                        val physical = if(rtl) -offset else offset
                        transformOrigin = TransformOrigin(if(physical > 0f) 1f else 0f, 1f)
                        rotationY = physical * -12f
                        cameraDistance = 24.dp.toPx()
                    }
                }
                Box(Modifier.fillMaxSize().then(pageTurn)) {
                    Surface(color = paper, contentColor = ink, modifier = Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(Modifier.widthIn(max = 620.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                Text(story.title, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                                Text(story.pages[index], fontFamily = FontFamily.Serif,
                                    fontSize = listOf(17, 21, 26)[fontIndex].sp,
                                    lineHeight = listOf(26, 32, 39)[fontIndex].sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PressTextButton({ scope.launch { pager.animateScrollToPage(page - 1) } }, enabled = page > 0 && !pager.isScrollInProgress) {
                        Text(tr("قبلی", "Previous"), color = ink.copy(alpha = if(page > 0) 1f else .4f))
                    }
                    Text("${page + 1} / ${story.pages.size}", style = MaterialTheme.typography.labelMedium)
                    PressTextButton({ scope.launch { pager.animateScrollToPage(page + 1) } }, enabled = page < story.pages.lastIndex && !pager.isScrollInProgress) {
                        Text(tr("بعدی", "Next"), color = ink.copy(alpha = if(page < story.pages.lastIndex) 1f else .4f))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PressIconButton({
                        when {
                            playing || (request && player == null) -> pause()
                            player != null -> player?.let(::play)
                            else -> { failed = false; request = true }
                        }
                    }) {
                        val stop = playing || (request && player == null)
                        AppIcon(if(stop) UiIcons.Pause else UiIcons.Play,
                            if(stop) tr("مکث روایت", "Pause narration") else tr("پخش روایت", "Play narration"), tint = ink)
                    }
                    Text(if(request && player == null) tr("در حال آماده‌سازی…", "Preparing…") else tr("روایت صوتی", "Narration"))
                    PressTextButton({ speedIndex = (speedIndex + 1) % speeds.size }) { Text("${speeds[speedIndex]}×", color = ink) }
                    PressTextButton({ player?.seekTo(0) }, enabled = player != null) { Text(tr("از اول", "Restart"), color = ink.copy(alpha = if(player != null) 1f else .4f)) }
                }
                if(failed) Text(tr("صدا در دسترس نیست. صدای این زبان را در تنظیمات گفتار اندروید نصب کن.", "Narration unavailable. Install an offline voice for this language in Android text-to-speech settings."), style = MaterialTheme.typography.bodySmall)
                val smaller = tr("کوچک کردن نوشته", "Decrease text size")
                val larger = tr("بزرگ کردن نوشته", "Increase text size")
                val changePaper = tr("رنگ بعدی کاغذ", "Next paper color")
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReaderCircle("−", smaller, ink, papers[paperIndex], fontIndex > 0) { fontIndex-- }
                        ReaderCircle("+", larger, ink, papers[paperIndex], fontIndex < 2) { fontIndex++ }
                        Spacer(Modifier.weight(1f))
                        ReaderCircle(null, "$changePaper: ${paperNames[nextPaper]}", ink, papers[nextPaper]) { paperIndex = nextPaper }
                    }
                }
            }
        }
    }
}

@Composable private fun ReaderCircle(symbol: String?, label: String, ink: Color, fill: Color,
    enabled: Boolean = true, onClick: () -> Unit) {
    PressIconButton(onClick, enabled = enabled) {
        Surface(Modifier.size(38.dp), shape = CircleShape, color = fill,
            border = androidx.compose.foundation.BorderStroke(1.dp, ink.copy(alpha = if(enabled) .65f else .25f))) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.semantics { contentDescription = label }) {
                if(symbol != null) Text(symbol, color = ink.copy(alpha = if(enabled) 1f else .35f), fontSize = 22.sp,
                    modifier = Modifier.clearAndSetSemantics {})
            }
        }
    }
}

private suspend fun synthesizeStoryPage(context: Context, locale: String, text: String, file: File): Boolean {
    val initialized = CompletableDeferred<Boolean>()
    val engine = TextToSpeech(context.applicationContext) { initialized.complete(it == TextToSpeech.SUCCESS) }
    try {
        if(withTimeoutOrNull(5_000) { initialized.await() } != true) return false
        val language = Locale.forLanguageTag(locale)
        val voices = engine.voices?.filter { it.locale.language == language.language && !it.isNetworkConnectionRequired }.orEmpty()
        val voice = voices.firstOrNull { it.locale.country == language.country } ?: voices.firstOrNull() ?: return false
        if(engine.setVoice(voice) != TextToSpeech.SUCCESS) return false
        val result = CompletableDeferred<Boolean>()
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { result.complete(true) }
            @Deprecated("Deprecated by Android")
            override fun onError(utteranceId: String?) { result.complete(false) }
        })
        if(engine.synthesizeToFile(text, Bundle(), file, "story-page") != TextToSpeech.SUCCESS) return false
        return withTimeoutOrNull(20_000) { result.await() } == true
    } finally {
        engine.stop()
        engine.shutdown()
    }
}
