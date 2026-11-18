package com.hydro.reedance.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydro.reedance.R
import com.hydro.reedance.data.AppModel
import com.hydro.reedance.data.s
import com.hydro.reedance.domain.GameWordDefinition
import com.hydro.reedance.domain.PremiumEntitlement
import com.hydro.reedance.domain.buildHoneycombBoard
import com.hydro.reedance.domain.gameWordKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object GameSounds {
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

@Composable fun GameScreen(model: AppModel, premium: () -> Unit) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pageActive = LocalPageActive.current
    DisposableEffect(context, pageActive) {
        if(pageActive) GameSounds.prepare(context)
        onDispose { if(pageActive) GameSounds.dispose() }
    }
    val subscribed by PremiumEntitlement.active.collectAsStateWithLifecycle()
    val catalog by model.catalog.collectAsStateWithLifecycle()
    val installedIds by model.installedIds.collectAsStateWithLifecycle()
    val progress by model.gameWordProgress(settings.targetLanguage).collectAsStateWithLifecycle(initialValue = emptyList())
    var definitions by remember { mutableStateOf<Map<String, GameWordDefinition>>(emptyMap()) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(settings.targetLanguage, subscribed, catalog, installedIds) {
        busy = true
        definitions = emptyMap()
        try {
            val indexed = withContext(Dispatchers.IO) { model.syncGameWords(settings.targetLanguage, subscribed) }
            definitions = withContext(Dispatchers.Default) { indexed.associateBy { key(it.songId, it.lexemeId) } }
        } finally { busy = false }
    }
    val byKey = definitions
    val availableProgress = remember(progress, byKey) { progress.filter { key(it.songId, it.lexemeId) in byKey } }
    val levels = remember(availableProgress) { availableProgress.filter { it.levelIndex != null }.groupBy { it.levelIndex!! }.toSortedMap() }
    val activeRound = levels.entries.firstOrNull { (_, words) -> words.any { !it.solved } }
    val roundIndex = activeRound?.key ?: ((levels.keys.maxOrNull() ?: -1) + 1)
    val words = activeRound?.value.orEmpty().sortedBy { it.positionInLevel }
    val current = words.firstOrNull { !it.solved }
    val roundTargets = words.mapNotNull { byKey[key(it.songId, it.lexemeId)] }
    val boardIdentity = roundTargets.joinToString("|") { "${it.songId}:${it.lexemeId}:${it.letterAnswer}" }
    val letterBoard = remember(boardIdentity) { buildHoneycombBoard(roundTargets) }
    val solvedTargetKeys = words.filter { it.solved }.map { gameWordKey(it.songId, it.lexemeId) }.toSet()
    val solvedCellIndices = words.filter { it.solved }.associate { word ->
        val saved = word.solvedCellIndices.split(',').mapNotNull { it.toIntOrNull() }
        gameWordKey(word.songId, word.lexemeId) to saved.ifEmpty { letterBoard.wordPaths[gameWordKey(word.songId, word.lexemeId)].orEmpty() }
    }
    val matchingInstalledSongs = catalog.filter { it.s("id") in installedIds && it.s("language") == settings.targetLanguage }
    val canUseAnyInstalledSong = matchingInstalledSongs.any { subscribed || it.s("access") != "paid" }
    val noDownloads = installedIds.isEmpty()
    val scroll = rememberScrollState()

    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp + LocalOverlayBottomInset.current), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Text(tr("گیم واژه‌سازی", "Word game"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(tr("واژه‌های آهنگ‌های دانلودشده‌ات", "Words from your downloaded songs"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                if(current != null && roundTargets.isNotEmpty()) {
                    HoneycombPuzzle(
                        model = model,
                        targetLanguage = settings.targetLanguage,
                        board = letterBoard,
                        targets = roundTargets,
                        solvedTargetKeys = solvedTargetKeys,
                        solvedCellIndices = solvedCellIndices
                    )
                    Panel {
                        Text(tr("واژه‌های این مرحله", "Words in this level"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        words.forEach { word ->
                            val target = byKey[key(word.songId, word.lexemeId)] ?: return@forEach
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .32f))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIcon(
                                    if(word.solved) UiIcons.CheckFilled else UiIcons.Words,
                                    null,
                                    Modifier.size(20.dp),
                                    if(word.solved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    target.translationFa.ifBlank { "—" },
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    if(word.solved) target.lemma else "${target.letterAnswer.length} ${tr("حرف", "letters")}",
                                    color = if(word.solved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if(word.solved) FontWeight.SemiBold else FontWeight.Normal
                                )
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
