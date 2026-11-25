package com.hydro.reedance.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.hydro.reedance.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable internal fun DialogueReader(demo: DialogueDemo, paperIndex: Int, dim: Boolean,
    onPaperChange: () -> Unit, onDimChange: () -> Unit) {
    val tabs = listOf(tr("چت", "Chat"), tr("لغات", "Words"), tr("گرامر", "Grammar"), tr("اصطلاحات", "Phrases"), tr("سؤالات", "Questions"))
    val pager = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    var playing by remember { mutableStateOf(false) }
    var singleTurn by remember { mutableStateOf<Int?>(null) }
    var current by rememberSaveable { mutableIntStateOf(0) }
    var startedDialogue by rememberSaveable { mutableStateOf(false) }
    var revealed by rememberSaveable { mutableIntStateOf(0) }
    var elapsed by remember { mutableIntStateOf(0) }
    var replay by remember { mutableIntStateOf(0) }
    var firstMode by rememberDialoguePreference("speaker_0:${demo.titleEn}", 0, SpeakerMode.entries.indices)
    var secondMode by rememberDialoguePreference("speaker_1:${demo.titleEn}", 0, SpeakerMode.entries.indices)
    var hideAll by rememberDialogueToggle("hide_all", false)
    var translationIndex by rememberDialoguePreference("translation", 0, DialogueTranslation.entries.indices)
    val translation = DialogueTranslation.entries[translationIndex]
    var listMode by rememberDialogueToggle("list_mode", true)
    var speedIndex by rememberDialoguePreference("speed", 1, 0..3)
    var fontIndex by rememberDialoguePreference("font", 1, 0..2)
    var appearance by remember { mutableStateOf(false) }
    val speed = listOf(.75f, 1f, 1.25f, 1.5f)[speedIndex]
    val activeTurn = singleTurn ?: current
    val turn = demo.turns.getOrNull(activeTurn)
    val modes = listOf(SpeakerMode.entries[firstMode], SpeakerMode.entries[secondMode])
    val context = LocalContext.current
    val audio = turn?.audio?.takeIf { it.isFile }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var audioFailed by remember(activeTurn, replay) { mutableStateOf(false) }
    val finished = rememberUpdatedState {
        elapsed = 0
        if(singleTurn != null) {
            playing = false
            singleTurn = null
        } else {
            current++
            if(current !in demo.turns.indices) playing = false
        }
    }
    val fail = rememberUpdatedState { audioFailed = true; playing = false }
    DisposableEffect(audio, activeTurn, replay) {
        if(audio == null) onDispose { }
        else {
            val engine = ExoPlayer.Builder(context).build()
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) { if(state == Player.STATE_ENDED) finished.value() }
                override fun onPlayerError(error: PlaybackException) { fail.value() }
            }
            engine.addListener(listener)
            engine.setMediaItem(MediaItem.fromUri(Uri.fromFile(audio)))
            engine.prepare()
            player = engine
            onDispose { engine.removeListener(listener); engine.release(); player = null }
        }
    }
    LaunchedEffect(player, playing, speed, turn, modes) {
        player?.let { engine ->
            engine.volume = if(turn != null && modes[turn.speaker].audio) 1f else 0f
            engine.setPlaybackSpeed(speed)
            engine.playWhenReady = playing
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { playing = false; player?.pause() }
    LaunchedEffect(playing, activeTurn, speed, replay, audio) {
        if(!playing || turn == null) return@LaunchedEffect
        revealed = maxOf(revealed, activeTurn + 1)
        if(audio == null) {
            val duration = (turn.text.length * 55).coerceIn(1400, 3600)
            while(elapsed < duration) { delay(75); elapsed += (75 * speed).toInt() }
            finished.value()
        }
    }
    fun selectTurn(index: Int) {
        singleTurn = index
        elapsed = 0
        replay++
        playing = true
    }
    fun playPause() {
        if(playing) { playing = false; return }
        startedDialogue = true
        if(singleTurn == null && current >= demo.turns.size) { current = 0; revealed = 0; elapsed = 0; replay++ }
        playing = true
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = 12.dp)) {
            ExpandedPillTabs(tabs.map { PillOption(it) }, pager.currentPage) { target -> scope.launch { pager.animateScrollToPage(target) } }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            if(page == 0) {
                val scheme = MaterialTheme.colorScheme
                Column(Modifier.fillMaxSize()) {
                    if(demo.turns.none { it.audio != null }) Text(tr("دموی متنی · بدون فایل صوتی", "Text demo · no recorded audio"),
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                    if(audioFailed) Text(tr("فایل صوتی پخش نشد.", "Could not play the recording."), color = scheme.error)
                    Surface(Modifier.weight(1f).fillMaxWidth(), color = scheme.background, contentColor = scheme.onBackground) {
                        Box(Modifier.fillMaxSize().dialogueWallpaper(scheme.primary, paperIndex)) {
                            DialogueMessages(demo, revealed, activeTurn.takeIf { playing } ?: -1, modes, hideAll, listMode, fontIndex, translation, ::selectTurn)
                            if(!startedDialogue && revealed == 0) {
                                DialoguePlayHint(Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 2.dp))
                            }
                        }
                    }
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                            SpeakerAvatar(demo.speakers[0], modes[0], true, Modifier.align(Alignment.CenterStart)) {
                                firstMode = modes[0].next().ordinal
                            }
                            PressIconButton(::playPause, Modifier.align(Alignment.Center).size(56.dp)) {
                                Surface(Modifier.fillMaxSize(), shape = CircleShape,
                                    color = scheme.primary, contentColor = scheme.onPrimary) {
                                    Box(contentAlignment = Alignment.Center) {
                                        AppIcon(if(playing) UiIcons.PauseFilled else UiIcons.PlayFilled,
                                            if(playing) tr("مکث", "Pause") else tr("پخش", "Play"), Modifier.size(24.dp))
                                    }
                                }
                            }
                            SpeakerAvatar(demo.speakers[1], modes[1], false, Modifier.align(Alignment.CenterEnd)) {
                                secondMode = modes[1].next().ordinal
                            }
                        }
                    }
                    val translationLabel = when(translation) {
                        DialogueTranslation.ORIGINAL -> tr("متن اصلی", "Original")
                        DialogueTranslation.SENTENCE -> tr("ترجمهٔ جمله", "Sentence translation")
                        DialogueTranslation.WORD -> tr("کلمه‌به‌کلمه", "Word by word")
                    }
                    val listState = tr("لیست", "List")
                    val pairState = tr("دو پیام آخر", "Last two messages")
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly) {
                        val speedLabel = tr("سرعت پخش", "Playback speed")
                        DialogueControl({ speedIndex = (speedIndex + 1) % 4 }, Modifier.semantics {
                            contentDescription = speedLabel
                            stateDescription = "${speed}×"
                        }, selected = speed != 1f) { Text("${speed}×", style = MaterialTheme.typography.labelMedium) }
                        DialogueControl({ listMode = !listMode }, Modifier.semantics {
                            stateDescription = if(listMode) listState else pairState
                        }, selected = !listMode) {
                            AppIcon(if(listMode) UiIcons.Lines else UiIcons.Notes, tr("لیست یا دو پیام آخر", "List or last two messages"), Modifier.size(19.dp))
                        }
                        DialogueControl({ hideAll = !hideAll }, selected = hideAll) {
                            AppIcon(if(hideAll) UiIcons.EyeClosed else UiIcons.Eye, tr("نمایش یا پنهان‌کردن پیام‌ها", "Show or hide messages"), Modifier.size(19.dp))
                        }
                        DialogueControl({ translationIndex = (translationIndex + 1) % DialogueTranslation.entries.size },
                            Modifier.semantics { stateDescription = translationLabel }, selected = translation != DialogueTranslation.ORIGINAL) {
                            AppIcon(when(translation) {
                                DialogueTranslation.ORIGINAL -> UiIcons.Original
                                DialogueTranslation.SENTENCE -> UiIcons.Lines
                                DialogueTranslation.WORD -> UiIcons.WordMode
                            }, tr("حالت ترجمهٔ چت", "Chat translation mode"), Modifier.size(19.dp))
                        }
                        DialogueControl({ appearance = true }) {
                            AppIcon(UiIcons.Settings, tr("تنظیمات ظاهر چت", "Chat appearance settings"), Modifier.size(19.dp))
                        }
                    }
                }
            } else DialogueLearningTab(demo, page)
        }
    }
    if(appearance) AlertDialog(onDismissRequest = { appearance = false }, title = { Text(tr("ظاهر چت", "Chat appearance")) }, text = {
        Column {
            SettingsAction(UiIcons.WordMode, tr("اندازهٔ نوشته", "Text size"), listOf(tr("کوچک", "Small"), tr("متوسط", "Medium"), tr("بزرگ", "Large"))[fontIndex]) { fontIndex = (fontIndex + 1) % 3 }
            SettingsAction(UiIcons.Light, tr("نور", "Brightness"), if(dim) tr("کم", "Dim") else tr("عادی", "Normal"), onDimChange)
            SettingsAction(UiIcons.System, tr("پس‌زمینه", "Background"), listOf(tr("سیستم", "System"), tr("کاهی", "Sepia"), tr("آبی", "Blue"))[paperIndex], onPaperChange)
        }
    }, confirmButton = { PressTextButton({ appearance = false }) { Text(tr("بستن", "Close")) } })
}

private enum class DialogueTranslation { ORIGINAL, SENTENCE, WORD }

@Composable private fun DialogueMessages(demo: DialogueDemo, revealed: Int, active: Int, modes: List<SpeakerMode>, hideAll: Boolean,
    listMode: Boolean, font: Int, translation: DialogueTranslation, playLine: (Int) -> Unit) {
    if(hideAll) {
        val activeSpeaker = demo.turns.getOrNull(active)?.speaker
        val listening = activeSpeaker != null && modes[activeSpeaker].audio
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DialogueListeningWave(listening)
                Text(tr("پیام‌ها پنهان‌اند", "Messages hidden"), style = MaterialTheme.typography.titleSmall)
            }
        }
        return
    }
    // Speaker visibility changes only the bubble contents, never the message keys or order.
    val revealedTurns = demo.turns.take(revealed).withIndex().toList()
    val messages = if(listMode) revealedTurns else revealedTurns.takeLast(2)
    val scroll = rememberLazyListState()
    val reduced = LocalReducedMotion.current
    LaunchedEffect(messages.lastOrNull()?.index, listMode) {
        if(listMode && messages.isNotEmpty()) {
            if(reduced) scroll.scrollToItem(messages.lastIndex) else scroll.animateScrollToItem(messages.lastIndex)
        }
    }
    LazyColumn(Modifier.fillMaxSize(), state = scroll, contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, if(listMode) Alignment.Bottom else Alignment.CenterVertically)) {
        items(messages, key = { it.index }) { item ->
            val entering = remember { MutableTransitionState(reduced || item.index != revealed - 1 || item.index != active) }
            SideEffect { entering.targetState = true }
            Box(if(reduced) Modifier else Modifier.animateItem(fadeInSpec = null)) {
                AnimatedVisibility(entering, enter = fadeIn(tween(180)) + slideInVertically(tween(260)) { it / 3 }) {
                    DialogueBubble(item.value, item.index == active, font, translation,
                        hidden = !modes[item.value.speaker].messages,
                        showWave = item.index == active && modes[item.value.speaker].audio) { playLine(item.index) }
                }
            }
        }
    }
}

/** A decorative listening cue; it doesn't sample microphone or audio data. */
@Composable private fun DialogueListeningWave(listening: Boolean, compact: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val phase = if(listening && !LocalReducedMotion.current) {
        rememberInfiniteTransition(label = "dialogue listening").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1250, easing = LinearEasing)), label = "wave phase")
    } else null
    val bars = if(compact) 5 else 9
    val waveSize = if(compact) Modifier.size(width = 24.dp, height = 18.dp)
        else Modifier.size(width = 110.dp, height = 58.dp).padding(horizontal = 16.dp, vertical = 11.dp)
    Surface(shape = CircleShape, color = if(compact) Color.Transparent else colors.primaryContainer,
        contentColor = colors.onPrimaryContainer) {
        Canvas(waveSize) {
            val angle = (phase?.value ?: 0f) * 2.0 * Math.PI
            repeat(bars) { bar ->
                val centerWeight = 1f - kotlin.math.abs(bar - bars / 2) / (bars - 1f)
                val pulse = if(phase == null) .55f else (.55 + .36 * kotlin.math.sin(angle + bar * .82)).toFloat()
                val half = size.height * .48f * centerWeight * pulse
                val x = size.width * (bar + .5f) / bars
                drawLine(colors.onPrimaryContainer.copy(alpha = if(listening) .94f else .48f),
                    Offset(x, size.height / 2f - half), Offset(x, size.height / 2f + half),
                    strokeWidth = (if(compact) 2.dp else 3.dp).toPx(), cap = StrokeCap.Round)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun DialogueBubble(turn: DialogueTurn, active: Boolean, font: Int, translation: DialogueTranslation,
    hidden: Boolean, showWave: Boolean, play: () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.fillMaxWidth(), contentAlignment = if(turn.speaker == 0) Alignment.CenterStart else Alignment.CenterEnd) {
            val colors = MaterialTheme.colorScheme
            val textSize = listOf(14, 17, 21)[font].sp
            val bubbleSize = if(hidden) Modifier.widthIn(min = 48.dp) else Modifier.fillMaxWidth(.84f)
            Surface(onClick = play, modifier = bubbleSize, shape = InsetControlShape,
                color = when { active -> colors.primaryContainer; turn.speaker == 1 -> colors.secondaryContainer; else -> colors.surfaceContainer },
                contentColor = when { active -> colors.onPrimaryContainer; turn.speaker == 1 -> colors.onSecondaryContainer; else -> colors.onSurface }) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if(hidden) {
                        Text("...", Modifier.align(Alignment.CenterHorizontally), fontSize = textSize)
                        return@Column
                    }
                    if(showWave) {
                        val playingLabel = tr("در حال پخش", "Playing")
                        Box(Modifier.semantics { contentDescription = playingLabel }) {
                            DialogueListeningWave(listening = true, compact = true)
                        }
                    }
                    if(translation == DialogueTranslation.WORD) {
                        val words = remember(turn.text) { turn.text.split(Regex("\\s+")).filter { it.isNotBlank() } }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            words.forEachIndexed { index, word ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(word, fontSize = textSize)
                                    turn.glosses.getOrNull(index)?.takeIf { it.isNotBlank() }?.let { gloss ->
                                        Text(gloss, fontSize = listOf(11, 12, 14)[font].sp, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                    } else Text(turn.text, fontSize = textSize)
                    if(translation == DialogueTranslation.SENTENCE) {
                        PersianText(turn.meaning, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable private fun DialoguePlayHint(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = InsetControlShape, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
            Text(tr("برای شروع گفت‌وگو روی پخش بزن", "Tap play to start the dialogue"),
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
        Canvas(Modifier.size(width = 14.dp, height = 7.dp)) {
            drawPath(Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }, color = colors.primaryContainer)
        }
    }
}

@Composable private fun DialogueControl(action: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false,
    content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    PressIconButton(action, modifier.size(48.dp)) {
        Surface(Modifier.size(40.dp), shape = NestedTileShape,
            color = if(selected) colors.primaryContainer else colors.surfaceContainerHigh,
            contentColor = if(selected) colors.onPrimaryContainer else colors.onSurface) {
            Box(contentAlignment = Alignment.Center) { content() }
        }
    }
}

@Composable private fun SpeakerAvatar(speaker: DialogueSpeaker, mode: SpeakerMode, first: Boolean, modifier: Modifier, change: () -> Unit) {
    val label = when(mode) {
        SpeakerMode.BOTH -> tr("صدا و پیام", "Audio and messages")
        SpeakerMode.MUTE -> tr("بی‌صدا", "Muted")
        SpeakerMode.LISTEN -> tr("فقط صدا", "Audio only")
        SpeakerMode.ROLE -> tr("نقش‌آفرینی", "Role-play")
    }
    Column(modifier.clickable(onClick = change).semantics { stateDescription = label }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val colors = MaterialTheme.colorScheme
        Surface(Modifier.size(40.dp), shape = CircleShape, color = if(first) colors.primary else colors.tertiary,
            contentColor = if(first) colors.onPrimary else colors.onTertiary) {
            Box(contentAlignment = Alignment.Center) {
                AppIcon(if(speaker.gender == DialogueGender.WOMAN) R.drawable.dialogue_avatar_woman else R.drawable.dialogue_avatar_man,
                    null, Modifier.size(28.dp))
            }
        }
        Text(speaker.name, style = MaterialTheme.typography.labelLarge)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable private fun DialogueLearningTab(demo: DialogueDemo, tab: Int) {
    val learning = demo.learning
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when(tab) {
            1 -> {
                item(key = "word-table") { Panel { LearningTable(learning.wordTable, fitWhenPossible = true) } }
                itemsIndexed(learning.words, key = { index, _ -> "word-$index" }) { _, word ->
                    Panel { WordLearningContent(word) }
                }
            }
            2 -> itemsIndexed(learning.grammar, key = { index, _ -> "grammar-$index" }) { _, note ->
                LearningNote(note, fitTablesWhenPossible = true)
            }
            3 -> {
                item(key = "phrase-table") { Panel { LearningTable(learning.phraseTable, fitWhenPossible = true) } }
                itemsIndexed(learning.phrases, key = { index, _ -> "phrase-$index" }) { _, phrase ->
                    Panel { PhraseLearningContent(phrase); WrittenPronunciation(phrase) }
                }
            }
            4 -> itemsIndexed(learning.questions, key = { index, _ -> "question-$index" }) { _, question ->
                DialogueQuestionCard(question)
            }
        }
    }
}

@Composable private fun DialogueQuestionCard(question: DialogueQuestion) {
    var answer by rememberSaveable { mutableIntStateOf(-1) }
    Panel {
        PersianText(question.prompt, style = MaterialTheme.typography.titleLarge)
        question.options.forEachIndexed { index, text ->
            val marker = if(answer >= 0 && index == question.correct) "✓ " else if(index == answer) "✕ " else ""
            PressOutlinedButton({ answer = index }, Modifier.fillMaxWidth(), enabled = answer < 0, shape = InsetControlShape) {
                PersianText(marker + text)
            }
        }
        if(answer >= 0) {
            SettingsDivider()
            PersianText(question.explanation, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
