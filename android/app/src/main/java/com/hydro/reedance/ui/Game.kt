package com.hydro.reedance.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydro.reedance.data.AppModel
import com.hydro.reedance.data.s
import com.hydro.reedance.domain.GameWordDefinition
import com.hydro.reedance.domain.PremiumEntitlement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private object GameSounds {
    private val lock = Any()
    private var tones: ToneGenerator? = null
    private var soundPool: SoundPool? = null
    private var winSoundId = 0
    private val loadedSounds = mutableSetOf<Int>()

    fun prepare(context: Context) = synchronized(lock) {
        if(soundPool != null) return@synchronized
        tones = ToneGenerator(AudioManager.STREAM_MUSIC, 42)
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        loadedSounds.clear()
        val pool = SoundPool.Builder().setAudioAttributes(attributes).setMaxStreams(3).build()
        soundPool = pool
        pool.setOnLoadCompleteListener { callbackPool, sampleId, status ->
            synchronized(lock) {
                if(soundPool === callbackPool && status == 0) loadedSounds += sampleId
            }
        }
        winSoundId = pool.load(context.applicationContext, R.raw.game_win, 1)
    }

    fun connect() = tones?.startTone(ToneGenerator.TONE_PROP_BEEP, 45)
    fun release() = tones?.startTone(ToneGenerator.TONE_PROP_NACK, 75)
    fun incorrect() = tones?.startTone(ToneGenerator.TONE_PROP_NACK, 150)

    fun win() = synchronized(lock) {
        val pool = soundPool
        if(pool != null && winSoundId != 0 && winSoundId in loadedSounds) {
            pool.play(winSoundId, .82f, .82f, 1, 0, 1f)
        } else tones?.startTone(ToneGenerator.TONE_PROP_ACK, 180)
    }

    fun dispose() = synchronized(lock) {
        soundPool?.release()
        soundPool = null
        winSoundId = 0
        loadedSounds.clear()
        tones?.release()
        tones = null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun GameScreen(model: AppModel, premium: () -> Unit) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    DisposableEffect(context) {
        GameSounds.prepare(context)
        onDispose { GameSounds.dispose() }
    }
    val subscribed by PremiumEntitlement.active.collectAsStateWithLifecycle()
    val catalog by model.catalog.collectAsStateWithLifecycle()
    val installedIds by model.installedIds.collectAsStateWithLifecycle()
    val progress by model.gameWordProgress(settings.targetLanguage).collectAsStateWithLifecycle(initialValue = emptyList())
    val definitions = remember { mutableStateMapOf<String, GameWordDefinition>() }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(settings.targetLanguage, subscribed, catalog, installedIds) {
        busy = true
        definitions.clear()
        try {
            val indexed = withContext(Dispatchers.IO) { model.syncGameWords(settings.targetLanguage, subscribed) }
            indexed.forEach { definitions[key(it.songId, it.lexemeId)] = it }
        } finally { busy = false }
    }
    val byKey = remember(definitions.toMap()) { definitions.toMap() }
    val availableProgress = remember(progress, byKey) { progress.filter { key(it.songId, it.lexemeId) in byKey } }
    val levels = remember(availableProgress) { availableProgress.filter { it.levelIndex != null }.groupBy { it.levelIndex!! }.toSortedMap() }
    val activeRound = levels.entries.firstOrNull { (_, words) -> words.any { !it.solved } }
    val roundIndex = activeRound?.key ?: ((levels.keys.maxOrNull() ?: -1) + 1)
    val words = activeRound?.value.orEmpty().sortedBy { it.positionInLevel }
    val current = words.firstOrNull { !it.solved }
    val definition = current?.let { byKey[key(it.songId, it.lexemeId)] }
    val matchingInstalledSongs = catalog.filter { it.s("id") in installedIds && it.s("language") == settings.targetLanguage }
    val canUseAnyInstalledSong = matchingInstalledSongs.any { subscribed || it.s("access") != "paid" }
    val noDownloads = installedIds.isEmpty()
    val scroll = rememberScrollState()

    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp + LocalOverlayBottomInset.current), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(tr("گیم واژه‌سازی", "Word game"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(tr("واژه‌های آهنگ‌های دانلودشده‌ات", "Words from your downloaded songs"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AppIcon(UiIcons.Quiz, tr("گیم", "Game"), Modifier.size(30.dp), MaterialTheme.colorScheme.primary)
        }
        when {
            noDownloads -> Panel {
                AppIcon(UiIcons.Library, null, Modifier.size(32.dp), MaterialTheme.colorScheme.primary)
                Text(tr("برای شروع بازی، حداقل یک آهنگ دانلود کن.", "Download at least one song to start playing."), style = MaterialTheme.typography.titleMedium)
                Text(tr("واژه‌های آهنگ‌های دانلودشده به مرحله‌های بازی اضافه می‌شوند.", "Words from your downloaded songs will appear in game rounds."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            matchingInstalledSongs.isEmpty() -> Panel {
                AppIcon(UiIcons.Library, null, Modifier.size(32.dp), MaterialTheme.colorScheme.primary)
                Text(tr("برای این زبان هنوز آهنگ دانلودشده‌ای نداری.", "No downloaded songs for this language yet."), style = MaterialTheme.typography.titleMedium)
                Text(tr("یک آهنگ به زبان آموزشی‌ات دانلود کن تا واژه‌هایش وارد بازی شوند.", "Download a song in your learning language to add its words to the game."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            !canUseAnyInstalledSong && !subscribed -> Panel {
                AppIcon(UiIcons.Crown, null, Modifier.size(32.dp), MaterialTheme.colorScheme.primary)
                Text(tr("آهنگ‌های دانلودشدهٔ زبان آموزشی‌ات پریمیوم‌اند.", "Your downloaded songs for this language are premium."), style = MaterialTheme.typography.titleMedium)
                Text(tr("با خرید اشتراک، واژه‌های آن‌ها هم به بازی اضافه می‌شوند.", "Subscribe to unlock their words in the game."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Action(tr("دیدن اشتراک پریمیوم", "Explore Premium"), Modifier.fillMaxWidth(), onClick = premium)
            }
            else -> {
                val playableCount = availableProgress.count { it.levelIndex != null }
                val solvedCount = availableProgress.count { it.solved }
                Panel {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("مرحلهٔ ${roundIndex + 1}", "Level ${roundIndex + 1}"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(tr("${words.count { it.solved }} از ${words.size} واژه", "${words.count { it.solved }} of ${words.size} words"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LinearProgressIndicator(progress = { if(words.isEmpty()) 0f else words.count { it.solved }.toFloat() / words.size }, modifier = Modifier.fillMaxWidth())
                }
                if(definition != null && current != null) {
                    Panel {
                        Text(tr("معنی واژه", "Word meaning"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        AnimatedContent(
                            targetState = definition,
                            transitionSpec = {
                                (slideInVertically { it / 4 } + fadeIn()) togetherWith
                                    (slideOutVertically { -it / 4 } + fadeOut())
                            },
                            label = "game-word"
                        ) { target ->
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(target.translationFa.ifBlank { target.translationEn.ifBlank { "—" } }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                if(target.translationEn.isNotBlank() && target.translationFa.isNotBlank()) Text(target.translationEn, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(target.songTitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    AnimatedContent(
                        targetState = definition,
                        transitionSpec = {
                            (slideInVertically { it / 3 } + fadeIn()) togetherWith
                                (slideOutVertically { -it / 3 } + fadeOut())
                        },
                        label = "game-letter-puzzle"
                    ) { target ->
                        val answer = target.letterAnswer
                        val targetWord = words.firstOrNull { it.songId == target.songId && it.lexemeId == target.lexemeId }
                        var letters by rememberSaveable(target.targetLanguage, target.songId, target.lexemeId) {
                            mutableStateOf(shuffledGameLetters(target.songId, target.lexemeId, answer))
                        }
                        var selected by rememberSaveable(target.targetLanguage, target.songId, target.lexemeId) { mutableStateOf(emptyList<Int>()) }
                        var feedback by rememberSaveable(target.targetLanguage, target.songId, target.lexemeId) { mutableStateOf("") }
                        var won by rememberSaveable(target.targetLanguage, target.songId, target.lexemeId) { mutableStateOf(false) }
                        var rejecting by rememberSaveable(target.targetLanguage, target.songId, target.lexemeId) { mutableStateOf(false) }
                        LaunchedEffect(rejecting, target.songId, target.lexemeId) {
                            if(rejecting) {
                                delay(520)
                                selected = emptyList()
                                feedback = ""
                                rejecting = false
                            }
                        }
                        val correctFeedback = tr("آفرین! واژه پیدا شد", "Correct! Word found")
                        val incorrectFeedback = tr("این واژه نیست؛ دوباره تلاش کن", "Not quite—try again")
                        val winColor = Color(0xFF2F9E62)
                        val boardColor by animateColorAsState(
                            if(won) winColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
                            label = "answer-board-win"
                        )
                        val boardTextColor by animateColorAsState(
                            if(won) Color.White else MaterialTheme.colorScheme.primary,
                            label = "answer-board-text"
                        )
                        Panel {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(tr("حروف را به ترتیب لمس کن", "Tap the letters in order"), style = MaterialTheme.typography.titleMedium)
                                PressIconButton(
                                    onClick = {
                                        selected = selected.dropLast(1)
                                        feedback = ""
                                        GameSounds.release()
                                    },
                                    modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                                    enabled = selected.isNotEmpty() && !won && !rejecting
                                ) { AppIcon(UiIcons.Backspace, tr("حذف حرف آخر", "Backspace"), Modifier.size(22.dp)) }
                            }
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(boardColor).padding(12.dp), horizontalArrangement = Arrangement.Center) {
                                val assembled = selected.mapNotNull { letters.getOrNull(it) }.joinToString("")
                                Text(assembled.ifEmpty { "····" }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = boardTextColor)
                            }
                            FlowRow(horizontalArrangement = Arrangement.Center, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                letters.forEachIndexed { index, letter ->
                                    val used = selected.contains(index)
                                    val tileColor by animateColorAsState(
                                        if(won) winColor else if(used) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f) else MaterialTheme.colorScheme.surface,
                                        label = "game-letter-tile-$index"
                                    )
                                    val tileTextColor by animateColorAsState(
                                        if(won) Color.White else if(used) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .35f) else MaterialTheme.colorScheme.onSurface,
                                        label = "game-letter-text-$index"
                                    )
                                    Surface(
                                        modifier = Modifier.padding(horizontal = 4.dp).size(48.dp).clickable(enabled = !used && !won && !rejecting) {
                                            val nextSelection = selected + index
                                            selected = nextSelection
                                            feedback = ""
                                            GameSounds.connect()
                                            if(nextSelection.size == answer.length) {
                                                val guess = nextSelection.mapNotNull { letters.getOrNull(it) }.joinToString("")
                                                if(guess.equals(answer, ignoreCase = true)) {
                                                    won = true
                                                    feedback = correctFeedback
                                                    GameSounds.win()
                                                    targetWord?.let {
                                                        model.solveGameWord(settings.targetLanguage, it.songId, it.lexemeId, revealDelayMs = 620)
                                                    }
                                                } else {
                                                    rejecting = true
                                                    feedback = incorrectFeedback
                                                    GameSounds.incorrect()

                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        color = tileColor,
                                        border = if(used || won) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        shadowElevation = if(used || won) 0.dp else 2.dp
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(letter.toString().uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tileTextColor)
                                        }
                                    }
                                }
                            }
                            if(feedback.isNotBlank()) Text(
                                feedback,
                                color = if(won) winColor else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    if(words.size > 1) {
                        Text(tr("واژه‌های این مرحله", "Words in this level"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        words.forEach { word ->
                            val item = byKey[key(word.songId, word.lexemeId)]
                            if(item != null) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                AppIcon(if(word.solved) UiIcons.CheckFilled else UiIcons.Words, null, Modifier.size(20.dp), if(word.solved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(10.dp))
                                Text(item.translationFa.ifBlank { item.translationEn }, modifier = Modifier.weight(1f))
                                if(word.solved) Text(item.lemma, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } else if(busy) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else if(playableCount == 0) {
                    Panel {
                        Text(tr("هنوز واژهٔ کافی برای یک مرحله نیست", "Not enough words for a round yet"), style = MaterialTheme.typography.titleMedium)
                        Text(tr("برای هر مرحله دست‌کم ۳ واژه لازم است. با دانلود آهنگ دیگری، واژه‌های تازه اضافه می‌شوند.", "Each round needs at least 3 words. Download another song to add more words."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Panel {
                        Text(tr("همهٔ واژه‌ها پیدا شدند!", "All words solved!"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(tr("${solvedCount} از ${playableCount} واژه تکمیل شد.", "$solvedCount of $playableCount words completed."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun key(songId: String, lexemeId: String) = "$songId::$lexemeId"

private fun shuffledGameLetters(songId: String, lexemeId: String, answer: String): String {
    val seed = songId.hashCode().toLong() * 31 + lexemeId.hashCode()
    val shuffled = answer.toList().shuffled(kotlin.random.Random(seed))
    return (if(shuffled.joinToString("").equals(answer, ignoreCase = true) && shuffled.size > 1) shuffled.reversed() else shuffled).joinToString("")
}
