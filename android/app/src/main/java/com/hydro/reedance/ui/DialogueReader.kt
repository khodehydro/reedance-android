package com.hydro.reedance.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable internal fun DialogueReader(demo: DialogueDemo) {
    val tabs = listOf(tr("چت", "Chat"), tr("لغات", "Words"), tr("گرامر", "Grammar"), tr("اصطلاحات", "Phrases"), tr("سؤالات", "Questions"))
    val pager = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    var playing by remember { mutableStateOf(false) }
    var singleTurn by remember { mutableStateOf<Int?>(null) }
    var current by rememberSaveable { mutableIntStateOf(0) }
    var revealed by rememberSaveable { mutableIntStateOf(0) }
    var elapsed by remember { mutableIntStateOf(0) }
    var replay by remember { mutableIntStateOf(0) }
    var firstMode by rememberSaveable { mutableIntStateOf(0) }
    var secondMode by rememberSaveable { mutableIntStateOf(0) }
    var hideAll by rememberSaveable { mutableStateOf(false) }
    var translationIndex by rememberSaveable { mutableIntStateOf(0) }
    val translation = DialogueTranslation.entries[translationIndex]
    var listMode by rememberSaveable { mutableStateOf(true) }
    var speedIndex by rememberSaveable { mutableIntStateOf(1) }
    var fontIndex by rememberSaveable { mutableIntStateOf(1) }
    var paperIndex by rememberSaveable { mutableIntStateOf(0) }
    var dim by rememberSaveable { mutableStateOf(false) }
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
                val paper = if(dim) Color(0xFF171717) else when(paperIndex) { 1 -> Color(0xFFF3E6CA); 2 -> Color(0xFFE0EDF0); else -> scheme.background }
                val ink = if(dim) Color.White else if(paperIndex != 0) Color(0xFF20252B) else scheme.onSurface
                Column(Modifier.fillMaxSize()) {
                    if(demo.turns.none { it.audio != null }) Text(tr("دموی متنی · بدون فایل صوتی", "Text demo · no recorded audio"),
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                    if(audioFailed) Text(tr("فایل صوتی پخش نشد.", "Could not play the recording."), color = scheme.error)
                    Surface(Modifier.weight(1f).fillMaxWidth(), color = paper, contentColor = ink) {
                        DialogueMessages(demo, revealed, activeTurn.takeIf { playing } ?: -1, modes, hideAll, listMode, fontIndex, translation, ::selectTurn)
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            SpeakerAvatar(demo.names[0], modes[0], true) { firstMode = modes[0].next().ordinal }
                            SpeakerAvatar(demo.names[1], modes[1], false) { secondMode = modes[1].next().ordinal }
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
                        PressIconButton(::playPause) {
                            AppIcon(if(playing) UiIcons.PauseFilled else UiIcons.PlayFilled, if(playing) tr("مکث", "Pause") else tr("پخش", "Play"))
                        }
                        PressTextButton({ speedIndex = (speedIndex + 1) % 4 }) { Text("${speed}×") }
                        PressIconButton({ listMode = !listMode }, Modifier.semantics {
                            stateDescription = if(listMode) listState else pairState
                        }) { AppIcon(UiIcons.Lines, tr("لیست یا دو پیام آخر", "List or last two messages")) }
                        PressIconButton({ hideAll = !hideAll }) {
                            AppIcon(if(hideAll) UiIcons.EyeClosed else UiIcons.Eye, tr("نمایش یا پنهان‌کردن پیام‌ها", "Show or hide messages"))
                        }
                        PressIconButton({ translationIndex = (translationIndex + 1) % DialogueTranslation.entries.size },
                            Modifier.semantics { stateDescription = translationLabel }) {
                            AppIcon(when(translation) {
                                DialogueTranslation.ORIGINAL -> UiIcons.Original
                                DialogueTranslation.SENTENCE -> UiIcons.Lines
                                DialogueTranslation.WORD -> UiIcons.WordMode
                            }, tr("حالت ترجمهٔ چت", "Chat translation mode"))
                        }
                        PressTextButton({ appearance = true }) { Text("Aa") }
                    }
                }
            } else DialogueLearningTab(demo, page)
        }
    }
    if(appearance) AlertDialog(onDismissRequest = { appearance = false }, title = { Text(tr("ظاهر چت", "Chat appearance")) }, text = {
        Column {
            SettingsAction(UiIcons.WordMode, tr("اندازهٔ نوشته", "Text size"), listOf(tr("کوچک", "Small"), tr("متوسط", "Medium"), tr("بزرگ", "Large"))[fontIndex]) { fontIndex = (fontIndex + 1) % 3 }
            SettingsAction(UiIcons.Light, tr("نور", "Brightness"), if(dim) tr("کم", "Dim") else tr("عادی", "Normal")) { dim = !dim }
            SettingsAction(UiIcons.System, tr("پس‌زمینه", "Background"), listOf(tr("سیستم", "System"), tr("کاهی", "Sepia"), tr("آبی", "Blue"))[paperIndex]) { paperIndex = (paperIndex + 1) % 3 }
        }
    }, confirmButton = { PressTextButton({ appearance = false }) { Text(tr("بستن", "Close")) } })
}

private enum class DialogueTranslation { ORIGINAL, SENTENCE, WORD }

@Composable private fun DialogueMessages(demo: DialogueDemo, revealed: Int, active: Int, modes: List<SpeakerMode>, hideAll: Boolean,
    listMode: Boolean, font: Int, translation: DialogueTranslation, playLine: (Int) -> Unit) {
    if(hideAll) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(tr("پیام‌ها پنهان‌اند", "Messages hidden")) }
        return
    }
    val visible = demo.turns.take(revealed).withIndex().filter { modes[it.value.speaker].messages }
    val messages = if(listMode) visible else visible.takeLast(2)
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
                    DialogueBubble(item.value, item.index == active, font, translation) { playLine(item.index) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun DialogueBubble(turn: DialogueTurn, active: Boolean, font: Int, translation: DialogueTranslation, play: () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.fillMaxWidth(), contentAlignment = if(turn.speaker == 0) Alignment.CenterStart else Alignment.CenterEnd) {
            val colors = MaterialTheme.colorScheme
            val textSize = listOf(14, 17, 21)[font].sp
            Surface(onClick = play, modifier = Modifier.fillMaxWidth(.84f), shape = InsetControlShape,
                color = if(active || turn.speaker == 1) colors.primaryContainer else colors.surface,
                contentColor = if(active || turn.speaker == 1) colors.onPrimaryContainer else colors.onSurface) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

@Composable private fun SpeakerAvatar(name: String, mode: SpeakerMode, first: Boolean, change: () -> Unit) {
    val label = when(mode) {
        SpeakerMode.BOTH -> tr("صدا و پیام", "Audio and messages")
        SpeakerMode.MUTE -> tr("بی‌صدا", "Muted")
        SpeakerMode.LISTEN -> tr("فقط صدا", "Audio only")
        SpeakerMode.ROLE -> tr("نقش‌آفرینی", "Role-play")
    }
    Column(Modifier.clickable(onClick = change).semantics { stateDescription = label }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(Modifier.size(40.dp), shape = CircleShape, color = if(first) Color(0xFF245198) else Color(0xFF17634F), contentColor = Color.White) {
            Box(contentAlignment = Alignment.Center) { Text(name.take(1)) }
        }
        Text(name, style = MaterialTheme.typography.labelLarge)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable private fun DialogueLearningTab(demo: DialogueDemo, tab: Int) {
    var answer by rememberSaveable { mutableIntStateOf(-1) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if(tab == 4) item {
            Panel {
                PersianText(demo.question, style = MaterialTheme.typography.titleLarge)
                demo.answers.forEachIndexed { index, text ->
                    PressOutlinedButton({ answer = index }, Modifier.fillMaxWidth(), enabled = answer < 0, shape = InsetControlShape) {
                        PersianText((if(answer >= 0 && index == demo.correct) "✓ " else if(index == answer) "✕ " else "") + text)
                    }
                }
            }
        } else itemsIndexed(when(tab) { 1 -> demo.words; 2 -> demo.grammar; else -> demo.phrases }) { _, item ->
            Panel { Foreign(item.first, large = true); PersianText(item.second) }
        }
    }
}
