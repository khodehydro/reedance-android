package com.hydro.reedance.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydro.reedance.data.AppModel
import com.hydro.reedance.data.s
import com.hydro.reedance.domain.GameWordDefinition
import com.hydro.reedance.domain.PremiumEntitlement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object GameSounds {
    private val player by lazy { ToneGenerator(AudioManager.STREAM_MUSIC, 42) }
    fun connect() { player.startTone(ToneGenerator.TONE_PROP_BEEP, 45) }
    fun release() { player.startTone(ToneGenerator.TONE_PROP_NACK, 75) }
    fun place() { player.startTone(ToneGenerator.TONE_PROP_ACK, 120) }
    fun incorrect() { player.startTone(ToneGenerator.TONE_PROP_NACK, 150) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun GameScreen(model: AppModel, premium: () -> Unit) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val subscribed by PremiumEntitlement.active.collectAsStateWithLifecycle()
    val catalog by model.catalog.collectAsStateWithLifecycle()
    val installedIds by model.installedIds.collectAsStateWithLifecycle()
    val progress by model.gameWordProgress(settings.targetLanguage).collectAsStateWithLifecycle(initialValue = emptyList())
    val definitions = remember { mutableStateMapOf<String, GameWordDefinition>() }
    var letters by rememberSaveable(settings.targetLanguage) { mutableStateOf("") }
    var selected by rememberSaveable(settings.targetLanguage) { mutableStateOf(emptyList<Int>()) }
    var feedback by rememberSaveable(settings.targetLanguage) { mutableStateOf("") }
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
            AppIcon(UiIcons.WordsFilled, tr("گیم", "Game"), Modifier.size(30.dp), MaterialTheme.colorScheme.primary)
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
                        AnimatedContent(targetState = definition, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "game-word") { target ->
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(target.translationFa.ifBlank { target.translationEn.ifBlank { "—" } }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                if(target.translationEn.isNotBlank() && target.translationFa.isNotBlank()) Text(target.translationEn, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(target.songTitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Panel {
                        Text(tr("حروف را به ترتیب لمس کن", "Tap the letters in order"), style = MaterialTheme.typography.titleMedium)
                        val answer = definition.letterAnswer
                        val shuffled = remember(current.songId, current.lexemeId) {
                            answer.toList().let { chars ->
                                val seed = current.songId.hashCode().toLong() * 31 + current.lexemeId.hashCode()
                                chars.shuffled(kotlin.random.Random(seed)).let { if(it.joinToString("").equals(answer, true) && it.size > 1) it.reversed() else it }
                            }.joinToString("")
                        }
                        LaunchedEffect(current.songId, current.lexemeId) { letters = shuffled; selected = emptyList(); feedback = "" }
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)).padding(12.dp), horizontalArrangement = Arrangement.Center) {
                            val assembled = selected.map { letters[it] }.joinToString("")
                            Text(assembled.ifEmpty { "····" }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        FlowRow(horizontalArrangement = Arrangement.Center, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            letters.forEachIndexed { index, letter ->
                                val used = selected.contains(index)
                                Surface(
                                    modifier = Modifier.padding(horizontal = 4.dp).size(48.dp).clickable(enabled = !used) {
                                        selected = selected + index
                                        feedback = ""
                                        GameSounds.connect()
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if(used) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f) else MaterialTheme.colorScheme.surface,
                                    border = if(used) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    shadowElevation = if(used) 0.dp else 2.dp
                                ) { Box(contentAlignment = Alignment.Center) { Text(letter.toString().uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if(used) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .35f) else MaterialTheme.colorScheme.onSurface) } }
                            }
                        }
                        val correctFeedback = tr("آفرین! واژه پیدا شد", "Correct! Word found")
                        val incorrectFeedback = tr("این واژه نیست؛ دوباره تلاش کن", "Not quite—try again")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = {
                                selected = selected.dropLast(1)
                                feedback = ""
                                GameSounds.release()
                            }, enabled = selected.isNotEmpty(), modifier = Modifier.weight(1f)) { Text(tr("حذف آخرین حرف", "Undo letter")) }
                            Button(onClick = {
                                val assembled = selected.map { letters[it] }.joinToString("")
                                if(assembled.equals(answer, ignoreCase = true)) {
                                    model.solveGameWord(settings.targetLanguage, current.songId, current.lexemeId)
                                    feedback = correctFeedback
                                    GameSounds.place()
                                    selected = emptyList()
                                } else {
                                    feedback = incorrectFeedback
                                    GameSounds.incorrect()
                                }
                            }, enabled = selected.isNotEmpty(), modifier = Modifier.weight(1f)) { Text(tr("جای‌گذاری واژه", "Place word")) }
                        }
                        if(feedback.isNotBlank()) Text(feedback, color = if(selected.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
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
