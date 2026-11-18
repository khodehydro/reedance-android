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
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.text.BreakIterator

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
            "Yağmur/باران/rain|yağıyordu/می‌بارید/was falling|ve/و/and|sokak/خیابان/street|sessizdi./ساکت بود/was quiet"),
        sampleLine("Islak montunu kapının yanına astı.", "پالتوی خیسش را کنار در آویخت.", "Deniz hung the wet coat by the door.",
            "Islak/خیس/wet|montunu/پالتویش/their coat|kapının yanına/کنار در/by the door|astı./آویخت/hung"),
        sampleLine("Mutfakta su ısıttı ve kendine bir fincan çay yaptı.", "در آشپزخانه آب گرم کرد و برای خودش یک فنجان چای درست کرد.", "In the kitchen, Deniz heated water and made a cup of tea.",
            "Mutfakta/در آشپزخانه/in the kitchen|su/آب/water|ısıttı/گرم کرد/heated|ve/و/and|kendine/برای خودش/for themself|bir fincan/یک فنجان/a cup of|çay/چای/tea|yaptı./درست کرد/made"),
        sampleLine("Çay hazır olunca pencerenin önündeki lambayı yaktı.", "وقتی چای آماده شد، چراغ جلوی پنجره را روشن کرد.", "When the tea was ready, Deniz turned on the lamp by the window.",
            "Çay/چای/tea|hazır olunca/وقتی آماده شد/when it was ready|pencerenin önündeki/جلوی پنجره/by the window|lambayı/چراغ را/the lamp|yaktı./روشن کرد/turned on")
    ), listOf(
        sampleLine("Pencerenin yanında oturdu ve eski bir şarkı açtı.", "کنار پنجره نشست و یک آهنگ قدیمی پخش کرد.", "Deniz sat by the window and put on an old song.",
            "Pencerenin yanında/کنار پنجره/by the window|oturdu/نشست/sat|ve/و/and|eski/قدیمی/old|bir/یک/a|şarkı/آهنگ/song|açtı./پخش کرد/put on"),
        sampleLine("Şarkıyı dinlerken bir arkadaşını hatırladı.", "هنگام گوش دادن به آهنگ، یاد یکی از دوستانش افتاد.", "While listening to the song, Deniz remembered a friend.",
            "Şarkıyı/آهنگ را/the song|dinlerken/هنگام گوش دادن/while listening|bir/یک/a|arkadaşını/دوستش/friend|hatırladı./به یاد آورد/remembered"),
        sampleLine("Eskiden bu şarkıyı birlikte dinlerlerdi.", "قبلاً این آهنگ را با هم گوش می‌دادند.", "They used to listen to this song together.",
            "Eskiden/قبلاً/back then|bu şarkıyı/این آهنگ را/this song|birlikte/با هم/together|dinlerlerdi./گوش می‌دادند/used to listen"),
        sampleLine("Bazen son otobüsü kaçırır, eve kadar yürürlerdi.", "گاهی به آخرین اتوبوس نمی‌رسیدند و تا خانه پیاده می‌رفتند.", "Sometimes they missed the last bus and walked all the way home.",
            "Bazen/گاهی/sometimes|son otobüsü/آخرین اتوبوس/the last bus|kaçırır,/از دست می‌دادند/missed|eve kadar/تا خانه/all the way home|yürürlerdi./پیاده می‌رفتند/would walk"),
        sampleLine("Deniz gülümsedi ve telefonunu masadan aldı.", "دنیز لبخند زد و گوشی‌اش را از روی میز برداشت.", "Deniz smiled and picked up the phone from the table.",
            "Deniz/دنیز/Deniz|gülümsedi/لبخند زد/smiled|ve/و/and|telefonunu/گوشی‌اش/their phone|masadan/از روی میز/from the table|aldı./برداشت/picked up")
    ), listOf(
        sampleLine("Arkadaşına bir mesaj yazdı: ‘Yarın bir çay içelim mi?’", "به دوستش پیام داد: «فردا با هم چای بخوریم؟»", "Deniz texted a friend: ‘Shall we have tea tomorrow?’",
            "Arkadaşına/به دوستش/to a friend|bir/یک/a|mesaj/پیام/message|yazdı:/نوشت/wrote|‘Yarın/فردا/tomorrow|bir/یک/a|çay/چای/tea|içelim mi?’/بخوریم؟/shall we drink?"),
        sampleLine("Cevap hemen geldi: ‘Olur, seni özledim.’", "جواب فوری آمد: «حتماً، دلم برایت تنگ شده.»", "The reply came at once: ‘Sure, I miss you.’",
            "Cevap/جواب/reply|hemen/فوری/right away|geldi:/آمد/came|‘Olur,/حتماً/sure|seni/تو را/you|özledim.’/دلم تنگ شده/I miss"),
        sampleLine("Ertesi gün küçük bir çay bahçesinde buluştular.", "روز بعد در یک چای‌خانهٔ باغی کوچک همدیگر را دیدند.", "The next day they met in a small tea garden.",
            "Ertesi gün/روز بعد/the next day|küçük/کوچک/small|bir/یک/a|çay bahçesinde/در باغ چای/in a tea garden|buluştular./همدیگر را دیدند/met"),
        sampleLine("İki çay söylediler ve uzun uzun konuştular.", "دو چای سفارش دادند و مدت زیادی گپ زدند.", "They ordered two teas and talked for a long time.",
            "İki/دو/two|çay/چای/teas|söylediler/سفارش دادند/ordered|ve/و/and|uzun uzun/مدت زیادی/for a long time|konuştular./گپ زدند/talked"),
        sampleLine("Deniz eve dönerken aynı şarkıyı yeniden açtı.", "دنیز در راه خانه دوباره همان آهنگ را پخش کرد.", "On the way home, Deniz put on the same song again.",
            "Deniz/دنیز/Deniz|eve dönerken/در راه خانه/on the way home|aynı şarkıyı/همان آهنگ را/the same song|yeniden/دوباره/again|açtı./پخش کرد/put on"),
        sampleLine("Bu kez şarkıyı yarıda kapatmadı.", "این بار آهنگ را نیمه‌کاره قطع نکرد.", "This time Deniz did not stop the song halfway through.",
            "Bu kez/این بار/this time|şarkıyı/آهنگ را/the song|yarıda/نیمه‌کاره/halfway through|kapatmadı./قطع نکرد/did not stop"),
        sampleLine("Durağa vardığında güneş batmak üzereydi.", "وقتی به ایستگاه رسید، خورشید نزدیک غروب بود.", "When Deniz reached the stop, the sun was about to set.",
            "Durağa/به ایستگاه/to the stop|vardığında/وقتی رسید/when they arrived|güneş/خورشید/the sun|batmak üzereydi./نزدیک غروب بود/was about to set"),
        sampleLine("Telefonuna yeni bir mesaj geldi.", "یک پیام تازه روی گوشی‌اش آمد.", "A new message arrived on Deniz’s phone.",
            "Telefonuna/روی گوشی‌اش/on their phone|yeni/تازه/new|bir/یک/a|mesaj/پیام/message|geldi./آمد/arrived"),
        sampleLine("Arkadaşı eski bir fotoğraf göndermişti.", "دوستش یک عکس قدیمی فرستاده بود.", "The friend had sent an old photo.",
            "Arkadaşı/دوستش/the friend|eski/قدیمی/old|bir/یک/an|fotoğraf/عکس/photo|göndermişti./فرستاده بود/had sent"),
        sampleLine("Fotoğrafta ikisi de yağmur altında gülüyordu.", "در عکس هر دو زیر باران می‌خندیدند.", "In the photo, both of them were laughing in the rain.",
            "Fotoğrafta/در عکس/in the photo|ikisi de/هر دو/both of them|yağmur altında/زیر باران/in the rain|gülüyordu./می‌خندیدند/were laughing"),
        sampleLine("Deniz fotoğrafı sakladı ve kısa bir cevap yazdı.", "دنیز عکس را ذخیره کرد و جواب کوتاهی نوشت.", "Deniz saved the photo and wrote a short reply.",
            "Deniz/دنیز/Deniz|fotoğrafı/عکس را/the photo|sakladı/ذخیره کرد/saved|ve/و/and|kısa/کوتاه/short|bir/یک/a|cevap/جواب/reply|yazdı./نوشت/wrote"),
        sampleLine("‘Haftaya çaylar benden,’ dedi.", "گفت: «هفتهٔ بعد چای‌ها مهمان من.»", "‘Tea is on me next week,’ Deniz said.",
            "‘Haftaya/هفتهٔ بعد/next week|çaylar/چای‌ها/tea|benden,’/مهمان من/on me|dedi./گفت/said"),
        sampleLine("Otobüs geldiğinde şarkının son notası duyuldu.", "وقتی اتوبوس رسید، آخرین نت آهنگ شنیده شد.", "When the bus arrived, the last note of the song played.",
            "Otobüs/اتوبوس/the bus|geldiğinde/وقتی رسید/when it arrived|şarkının/آهنگ/of the song|son notası/آخرین نت/the last note|duyuldu./شنیده شد/was heard"),
        sampleLine("Deniz kulaklığını çıkardı ve açık kapıya doğru yürüdü.", "دنیز هدفونش را درآورد و به سمت در باز رفت.", "Deniz took off the headphones and walked toward the open door.",
            "Deniz/دنیز/Deniz|kulaklığını/هدفونش/the headphones|çıkardı/درآورد/took off|ve/و/and|açık kapıya doğru/به سمت در باز/toward the open door|yürüdü./رفت/walked")
    )
))

private fun storyProse(text: String, locale: String): List<StoryLine> {
    val sentences = BreakIterator.getSentenceInstance(Locale.forLanguageTag(locale))
    return text.lineSequence().flatMap { paragraph ->
        sentences.setText(paragraph)
        buildList {
            var start = sentences.first()
            var end = sentences.next()
            while(end != BreakIterator.DONE) {
                val sentence = paragraph.substring(start, end).trim()
                if(sentence.isNotEmpty()) {
                    var remainder = sentence
                    while(remainder.length > 220) {
                        val split = remainder.lastIndexOf(' ', 220).takeIf { it > 0 } ?: break
                        add(StoryLine(remainder.substring(0, split)))
                        remainder = remainder.substring(split + 1).trimStart()
                    }
                    add(StoryLine(remainder))
                }
                start = end
                end = sentences.next()
            }
        }.asSequence()
    }.toList()
}

internal fun lessonStory(lesson: JsonObject?, songId: String): LessonStory? {
    if(lesson == null || lesson.s("songId") != songId) return null
    val story = lesson.get("story")?.takeIf { it.isJsonObject }?.asJsonObject
    val title = story?.s("title").orEmpty()
    val locale = lesson.s("locale").ifBlank { lesson.s("targetLanguage") }
    val pages = story?.get("pages")?.takeIf { it.isJsonArray }?.asJsonArray
        ?.mapNotNull { page ->
            when {
                page.isJsonPrimitive && page.asJsonPrimitive.isString -> storyProse(page.asString, locale)
                page.isJsonObject -> page.asJsonObject.a("lines").map { line ->
                    StoryLine(line.s("text"), line.tr("meaning", true), line.tr("meaning", false),
                        line.a("words").map { StoryGloss(it.s("text"), it.tr("meaning", true), it.tr("meaning", false)) })
                }
                else -> emptyList()
            }.filter { it.text.isNotBlank() }.takeIf { it.isNotEmpty() }
        }.orEmpty().ifEmpty { listOf(storyProse(story?.s("text").orEmpty(), locale)).filter { it.isNotEmpty() } }
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
    val lines = remember(story) { story.pages.flatten() }
    var pageStarts by remember(story) { mutableStateOf(listOf(0)) }
    var readingAnchor by rememberSaveable { mutableIntStateOf(0) }
    val pager = rememberPagerState { pageStarts.size }
    fun applyPagination(starts: List<Int>) {
        if(pageStarts == starts) return
        val target = starts.indexOfLast { it <= readingAnchor }.coerceAtLeast(0)
        pageStarts = starts
        pager.requestScrollToPage(target)
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.drop(1).collect { settled ->
            if(!pager.isScrollInProgress) pageStarts.getOrNull(settled)?.let { readingAnchor = it }
        }
    }
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
    val page = pager.settledPage.coerceIn(0, pageStarts.lastIndex)
    val start = pageStarts[page]
    val end = pageStarts.getOrElse(page + 1) { lines.size }
    val narration = remember(lines, start, end) { lines.subList(start, end).joinToString(" ") { it.text } }
    var request by remember(start, end) { mutableStateOf(false) }
    var player by remember(start, end) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(start, end) { mutableStateOf(false) }
    var failed by remember(start, end) { mutableStateOf(false) }
    var position by remember(start, end) { mutableIntStateOf(0) }
    var duration by remember(start, end) { mutableIntStateOf(0) }
    var dragging by remember(start, end) { mutableStateOf<Float?>(null) }
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
    LaunchedEffect(story, start, end, request) {
        if(!request) return@LaunchedEffect
        var output: File? = null
        var audio: MediaPlayer? = null
        try {
            val file = File.createTempFile("story-", ".wav", context.cacheDir).also { output = it }
            check(synthesizeStoryPage(context, story.locale, narration, file))
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
                Text(story.title, Modifier.padding(horizontal = 52.dp), fontFamily = FontFamily.Serif,
                    style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                PressIconButton(back, Modifier.align(Alignment.CenterStart)) { AppIcon(UiIcons.Back, tr("بازگشت", "Back")) }
            }
            StoryViewport(lines, meaningMode, fontIndex, fa, ink, ::applyPagination,
                Modifier.fillMaxWidth().weight(1f).background(papers[paperIndex])) {
                HorizontalPager(pager, modifier = Modifier.fillMaxSize()) { index ->
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
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                                Column(Modifier.widthIn(max = 620.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(StoryPagePadding),
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(StoryLineSpacing)) {
                                    StoryPageText(lines.subList(pageStarts[index], pageStarts.getOrElse(index + 1) { lines.size }),
                                        meaningMode, fontIndex, fa, ink)
                                }
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
                    Text("${page + 1} / ${pageStarts.size}", style = MaterialTheme.typography.labelMedium)
                    PressTextButton({ scope.launch { pager.animateScrollToPage(page + 1) } }, enabled = page < pageStarts.lastIndex && !pager.isScrollInProgress) {
                        Text(tr("بعدی", "Next"), color = ink.copy(alpha = if(page < pageStarts.lastIndex) 1f else .4f))
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
                        if(request && player == null) CircularProgressIndicator(Modifier.size(20.dp), color = ink, strokeWidth = 2.dp)
                        else AppIcon(if(stop) UiIcons.PauseFilled else UiIcons.PlayFilled,
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
                if(failed) AlertDialog(
                    onDismissRequest = { failed = false },
                    title = { Text(tr("صدا در دسترس نیست", "Narration unavailable")) },
                    text = { Text(tr("صدای این زبان را در تنظیمات گفتار اندروید نصب کن.", "Install an offline voice for this language in Android text-to-speech settings.")) },
                    confirmButton = { PressTextButton({ failed = false }) { Text(tr("باشه", "OK")) } }
                )
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

private val StoryPagePadding = 24.dp
private val StoryLineSpacing = 12.dp

internal fun storyPageStarts(heights: List<Int>, availableHeight: Int, spacing: Int): List<Int> = buildList {
    add(0)
    var used = 0
    heights.forEachIndexed { index, height ->
        if(index > last() && used + spacing + height > availableHeight) {
            add(index)
            used = 0
        }
        used += if(index == last()) height else spacing + height
    }
}

@Composable private fun StoryViewport(lines: List<StoryLine>, mode: Int, font: Int, fa: Boolean,
    ink: Color, onPages: (List<Int>) -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    SubcomposeLayout(modifier) { constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val padding = StoryPagePadding.roundToPx() * 2
        val readingWidth = (minOf(width, 620.dp.roundToPx()) - padding).coerceAtLeast(0)
        // Measure the same content used in the reader, without placing a duplicate on screen.
        val probes = subcompose("measure") {
            lines.forEach { line -> Box { StoryPageText(listOf(line), mode, font, fa, ink) } }
        }
        val heights = probes.map { it.measure(Constraints.fixedWidth(readingWidth)).height }
        val starts = storyPageStarts(heights, (height - padding).coerceAtLeast(1), StoryLineSpacing.roundToPx())
        val reader = subcompose("reader") {
            SideEffect { onPages(starts) }
            content()
        }.single().measure(Constraints.fixed(width, height))
        layout(width, height) { reader.placeRelative(0, 0) }
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
