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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.JsonObject
import com.hydro.reedance.data.AppModel
import com.hydro.reedance.data.LearningLanguage
import com.hydro.reedance.data.s
import com.hydro.reedance.data.a
import com.hydro.reedance.data.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale

internal data class StoryGloss(val text: String, val meaningFa: String, val meaningEn: String) {
    fun meaning(fa: Boolean) = if(fa) meaningFa.ifBlank { meaningEn } else meaningEn.ifBlank { meaningFa }
}
internal data class StoryLine(val text: String, val meaningFa: String = "", val meaningEn: String = "", val words: List<StoryGloss> = emptyList()) {
    fun meaning(fa: Boolean) = if(fa) meaningFa.ifBlank { meaningEn } else meaningEn.ifBlank { meaningFa }
}
internal data class LessonStory(val title: String, val locale: String, val pages: List<List<StoryLine>>)

private fun sampleLine(text: String, fa: String, en: String, glosses: String): StoryLine =
    StoryLine(text, fa, en, glosses.split('|').map { entry ->
        val (word, persian, english) = entry.split('/')
        StoryGloss(word, persian, english)
    })

private val sampleStory = LessonStory("Bir fincan çay", "tr-TR", listOf(
    listOf(
        sampleLine("Deniz akşam eve döndü.", "دنیز عصر به خانه برگشت.", "Deniz came home in the evening.",
            "Deniz/دنیز/Deniz|akşam/عصر/in the evening|eve/به خانه/home|döndü./برگشت/returned"),
        sampleLine("Yağmur yağıyordu ve sokak sessizdi.", "باران می‌بارید و خیابان ساکت بود.", "It was raining and the street was quiet.",
            "Yağmur/باران/rain|yağıyordu/می‌بارید/was falling|ve/و/and|sokak/خیابان/street|sessizdi./ساکت بود/was quiet")
    ), listOf(
        sampleLine("Pencerenin yanında oturdu ve eski bir şarkı açtı.", "کنار پنجره نشست و یک آهنگ قدیمی پخش کرد.", "Deniz sat by the window and put on an old song.",
            "Pencerenin yanında/کنار پنجره/by the window|oturdu/نشست/sat|ve/و/and|eski/قدیمی/old|bir/یک/a|şarkı/آهنگ/song|açtı./پخش کرد/put on"),
        sampleLine("Şarkıyı dinlerken bir arkadaşını hatırladı.", "هنگام گوش دادن به آهنگ، یاد یکی از دوستانش افتاد.", "While listening to the song, Deniz remembered a friend.",
            "Şarkıyı/آهنگ را/the song|dinlerken/هنگام گوش دادن/while listening|bir/یک/a|arkadaşını/دوستش/friend|hatırladı./به یاد آورد/remembered")
    ), listOf(
        sampleLine("Arkadaşına bir mesaj yazdı: ‘Yarın bir çay içelim mi?’", "به دوستش پیام داد: «فردا با هم چای بخوریم؟»", "Deniz texted a friend: ‘Shall we have tea tomorrow?’",
            "Arkadaşına/به دوستش/to a friend|bir/یک/a|mesaj/پیام/message|yazdı:/نوشت/wrote|‘Yarın/فردا/tomorrow|bir/یک/a|çay/چای/tea|içelim mi?’/بخوریم؟/shall we drink?"),
        sampleLine("Cevap hemen geldi: ‘Olur, seni özledim.’", "جواب فوری آمد: «حتماً، دلم برایت تنگ شده.»", "The reply came at once: ‘Sure, I miss you.’",
            "Cevap/جواب/reply|hemen/فوری/right away|geldi:/آمد/came|‘Olur,/حتماً/sure|seni/تو را/you|özledim.’/دلم تنگ شده/I miss")
    )
))

internal fun lessonStory(lesson: JsonObject?, songId: String): LessonStory? {
    if(lesson == null || lesson.s("songId") != songId) return null
    val story = lesson.get("story")?.takeIf { it.isJsonObject }?.asJsonObject
    val title = story?.s("title").orEmpty()
    val pages = story?.get("pages")?.takeIf { it.isJsonArray }?.asJsonArray
        ?.mapNotNull { page ->
            when {
                page.isJsonPrimitive && page.asJsonPrimitive.isString -> listOf(StoryLine(page.asString))
                page.isJsonObject -> page.asJsonObject.a("lines").map { line ->
                    StoryLine(line.s("text"), line.tr("meaning", true), line.tr("meaning", false),
                        line.a("words").map { StoryGloss(it.s("text"), it.tr("meaning", true), it.tr("meaning", false)) })
                }
                else -> emptyList()
            }.filter { it.text.isNotBlank() }.takeIf { it.isNotEmpty() }
        }.orEmpty()
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
    var meaningMode by rememberSaveable { mutableIntStateOf(0) }
    val fa = LearningLanguage.persian
    val hasMeaning = story.pages.flatten().any { it.meaning(fa).isNotBlank() || it.words.isNotEmpty() }
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
    SideEffect {
        // The paper draws edge-to-edge; only the content is inset below.
        (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = paperIndex != 2
                isAppearanceLightNavigationBars = paperIndex != 2
            }
        }
    }
    val page = pager.settledPage
    var request by remember(page) { mutableStateOf(false) }
    var player by remember(page) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(page) { mutableStateOf(false) }
    var failed by remember(page) { mutableStateOf(false) }
    var position by remember(page) { mutableIntStateOf(0) }
    var duration by remember(page) { mutableIntStateOf(0) }
    var dragging by remember(page) { mutableStateOf<Float?>(null) }
    val scrolling by rememberUpdatedState(pager.isScrollInProgress)
    fun pause() {
        val current = player
        if(current == null) request = false else if(playing) {
            current.pause()
            position = current.currentPosition
        }
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
    LaunchedEffect(player, playing) {
        val current = player ?: return@LaunchedEffect
        while(playing && player === current) {
            position = current.currentPosition
            delay(100)
        }
    }
    LaunchedEffect(speedIndex) { if(playing) player?.let(::play) }
    LaunchedEffect(story, page, request) {
        if(!request) return@LaunchedEffect
        var output: File? = null
        var audio: MediaPlayer? = null
        try {
            val file = File.createTempFile("story-", ".wav", context.cacheDir).also { output = it }
            check(synthesizeStoryPage(context, story.locale, story.pages[page].joinToString(" ") { it.text }, file))
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
            duration = prepared.duration.coerceAtLeast(0)
            prepared.setOnCompletionListener {
                position = duration
                playing = false
            }
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
            position = 0
            duration = 0
            dragging = null
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
            HorizontalPager(pager, modifier = Modifier.fillMaxWidth().weight(1f).background(papers[paperIndex])) { index ->
                val paper = papers[paperIndex]
                val pageTurn = Modifier.drawWithContent {
                    drawContent()
                    val offset = pager.currentPage - index + pager.currentPageOffsetFraction
                    val strength = kotlin.math.sin(kotlin.math.abs(offset).coerceIn(0f, 1f) * Math.PI).toFloat()
                    if(!reduced && strength > .001f && size.width > 0f) {
                        val rightEdge = if(rtl) offset < 0f else offset > 0f
                        val width = minOf(42.dp.toPx(), size.width)
                        val edge = if(rightEdge) size.width else 0f
                        val inner = if(rightEdge) edge - width else width
                        drawRect(
                            brush = Brush.linearGradient(
                                listOf(Color.Black.copy(alpha = .19f * strength), Color.Transparent),
                                start = Offset(edge, 0f), end = Offset(inner, 0f)
                            ),
                            topLeft = Offset(minOf(edge, inner), 0f),
                            size = androidx.compose.ui.geometry.Size(width, size.height)
                        )
                    }
                }
                Box(Modifier.fillMaxSize().then(pageTurn)) {
                    Surface(color = paper, contentColor = ink, modifier = Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(Modifier.widthIn(max = 620.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                Text(story.title, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                                StoryPageText(story.pages[index], meaningMode, fontIndex, fa, ink)
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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PressIconButton({
                        when {
                            playing || (request && player == null) -> pause()
                            player != null -> player?.let(::play)
                            else -> { failed = false; request = true }
                        }
                    }) {
                        val stop = playing || (request && player == null)
                        AppIcon(if(stop) UiIcons.PauseFilled else UiIcons.PlayFilled,
                            if(stop) tr("مکث روایت", "Pause narration") else tr("پخش روایت", "Play narration"), tint = ink)
                    }
                    Box(Modifier.weight(1f)) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            WaveSeekBar(
                                value = dragging ?: position.toFloat(), duration = duration.toFloat(), playing = playing,
                                onValueChange = { dragging = it },
                                onValueChangeFinished = {
                                    dragging?.toInt()?.coerceIn(0, duration)?.let { target ->
                                        player?.seekTo(target)
                                        position = target
                                    }
                                    dragging = null
                                },
                                ink = ink, enabled = duration > 0 && player != null
                            )
                        }
                    }
                    PressTextButton({ speedIndex = (speedIndex + 1) % speeds.size }) { Text("${speeds[speedIndex]}×", color = ink) }
                }
                if(request && player == null) Text(tr("در حال آماده‌سازی…", "Preparing…"), style = MaterialTheme.typography.bodySmall)
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
                        if(hasMeaning) {
                            val labels = listOf(tr("متن اصلی", "Original"), tr("ترجمهٔ خطی", "Line by line"), tr("کلمه‌به‌کلمه", "Word by word"))
                            val icons = listOf(UiIcons.Original, UiIcons.Lines, UiIcons.WordMode)
                            val meaningLabel = tr("حالت معنی", "Translation mode")
                            ReaderCircle(null, "$meaningLabel: ${labels[meaningMode]}", ink, papers[paperIndex], icon = icons[meaningMode]) {
                                meaningMode = (meaningMode + 1) % labels.size
                            }
                        }
                        ReaderCircle(null, "$changePaper: ${paperNames[nextPaper]}", ink, papers[nextPaper]) { paperIndex = nextPaper }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun StoryPageText(lines: List<StoryLine>, mode: Int, size: Int, fa: Boolean, ink: Color) {
    val font = listOf(17, 21, 26)[size].sp
    val lineHeight = listOf(26, 32, 39)[size].sp
    val glossSize = listOf(13, 15, 18)[size].sp
    lines.forEach { line ->
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if(mode == 2 && line.words.isNotEmpty()) {
                // The supported story source languages are left-to-right, even in the Persian UI.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        line.words.forEach { word ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(word.text, fontFamily = FontFamily.Serif, fontSize = font, textAlign = TextAlign.Center)
                                if(word.meaning(fa).isNotBlank()) Text(word.meaning(fa), fontSize = glossSize,
                                    color = ink.copy(alpha = .77f), textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            } else {
                Text(line.text, fontFamily = FontFamily.Serif, fontSize = font, lineHeight = lineHeight, textAlign = TextAlign.Center)
                if(mode == 1 && line.meaning(fa).isNotBlank()) Text(line.meaning(fa), fontSize = glossSize,
                    color = ink.copy(alpha = .77f), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable private fun ReaderCircle(symbol: String?, label: String, ink: Color, fill: Color,
    enabled: Boolean = true, icon: Int? = null, onClick: () -> Unit) {
    PressIconButton(onClick, enabled = enabled) {
        Surface(Modifier.size(38.dp), shape = CircleShape, color = fill,
            border = androidx.compose.foundation.BorderStroke(1.dp, ink.copy(alpha = if(enabled) .65f else .25f))) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.semantics { contentDescription = label }) {
                if(icon != null) AppIcon(icon, null, Modifier.size(20.dp), tint = ink)
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
