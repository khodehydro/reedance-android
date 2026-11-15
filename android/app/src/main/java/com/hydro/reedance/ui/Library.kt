package com.hydro.reedance.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydro.reedance.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable fun ReviewScreen(model: AppModel, quiz: () -> Unit) {
    val reviews by model.reviews.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while(true) { delay(1000); now = System.currentTimeMillis() } }
    val due = reviews.filter { it.dueAt <= now }
    val card = due.firstOrNull()
    val reviewLesson by produceState<com.google.gson.JsonObject?>(null, card?.songId) {
        value = null
        value = card?.let { try { model.lessonForReview(it.songId) } catch(cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch(_: Exception) { null } }
    }
    val word = reviewLesson?.takeIf { it.s("songId") == card?.songId }?.a("vocabulary")?.find { it.s("lexemeId") == card?.lexemeId }
    var reveal by remember(card?.songId, card?.lexemeId) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current
    val fa = LearningLanguage.persian
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp + LocalOverlayBottomInset.current), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Heading(tr("جعبهٔ لایتنر", "Words that stay"), tr("یادگیری، یک مرور کوتاه در هر روز.", "A little practice. A lasting memory."))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { (1..5).forEach { stage -> Column {
            Text("$stage", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text("${reviews.count { it.stage == stage }}", style = MaterialTheme.typography.bodySmall)
        } } }
        Text(tr("${due.size} واژه آمادهٔ مرور", "${due.size} words ready to review"))
        if(card != null && word != null) {
            Deck("${card.songId}:${card.lexemeId}") {
                Panel(tone = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(tr("کارت واژه · خانهٔ ${card.stage}", "WORD CARD · STAGE ${card.stage}"), style = MaterialTheme.typography.labelMedium)
                    Foreign(word.s("lemma"), large = true)
                    WrittenPronunciation(word)
                    if(!reveal) Action(tr("برگرداندن کارت", "Reveal meaning")) { reveal = true }
                    else {
                        PersianText(word.tr("translation", fa), style = MaterialTheme.typography.titleLarge)
                        Foreign(word.obj("example").s("text")); PersianText(word.obj("example").tr("translation", fa))
                        Action(tr("یادم بود", "Remembered"), enabled = !busy) {
                            if(!busy) { busy = true; scope.launch { model.review(card, true); if(!reduced) delay(220); busy = false } }
                        }
                        PressOutlinedButton({ if(!busy) { busy = true; scope.launch { model.review(card, false); if(!reduced) delay(220); busy = false } } }, enabled = !busy) { Text(tr("دوباره مرور کنم", "Review again")) }
                    }
                }
            }
            PressOutlinedButton({ card?.let { scope.launch { model.openInstalled(it.songId); quiz() } } }, Modifier.fillMaxWidth()) { Text(tr("مرور با سؤال", "Practice with questions")) }
        } else Panel {
            Text(if(reviews.isEmpty()) tr("در تب لغت‌های آهنگ، واژه‌ای را نشان کن تا به جعبه‌ات بیاید.", "Save a word in the song lesson to start your collection.") else if(card != null) tr("برای نمایش کارت‌ها، بستهٔ آهنگ را دوباره نصب کن. پیشرفتت حفظ شده است.", "Reinstall the song to view these cards. Your progress is preserved.") else tr("عالی بود! واژه‌ها در موعد بعدی برمی‌گردند.", "All caught up! Your words will return when they are due."))
        }
        Text(tr("پاسخ درست: خانهٔ بعد با فاصلهٔ ۲، ۴، ۸ و ۱۶ روز. فراموشی: خانهٔ اول، یک دقیقهٔ دیگر.", "Remembered: next stage in 2, 4, 8 or 16 days. Forgotten: stage one, due in one minute."), style = MaterialTheme.typography.bodySmall)
    }
}
@Composable fun LibraryScreen(model: AppModel, song: (String) -> Unit) {
    val bookmarks by model.bookmarks.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val catalog by model.catalog.collectAsStateWithLifecycle()
    val installedIds by model.installedIds.collectAsStateWithLifecycle()
    var index by remember(settings.targetLanguage) { mutableIntStateOf(0) }
    var reveal by remember(index) { mutableStateOf(false) }
    val fa = LearningLanguage.persian
    val savedLessons by produceState<Map<String, com.google.gson.JsonObject>>(emptyMap(), bookmarks) {
        value = bookmarks.map { it.songId }.distinct().mapNotNull { id ->
            try { model.lessonForReview(id)?.let { id to it } } catch(cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch(_: Exception) { null }
        }.toMap()
    }
    val savedPhrases = bookmarks.filter { it.itemType in listOf("idioms", "sentences") }.mapNotNull { mark -> savedLessons[mark.songId]?.a(mark.itemType)?.find { it.s("id") == mark.itemId } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp + LocalOverlayBottomInset.current), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Heading(tr("کتابخانهٔ تو", "Your little collection"), tr("چیزهایی که می‌خواهی همراهت بمانند.", "Keep the words and lines that speak to you."))
        if(savedPhrases.isNotEmpty()) {
            val current = savedPhrases[index.mod(savedPhrases.size)]
            Deck(index) { Panel(tone = MaterialTheme.colorScheme.secondaryContainer) {
                Text(tr("عبارت نشان‌شده", "SAVED LINE"), style = MaterialTheme.typography.labelMedium)
                Foreign(current.s("text"), large = true)
                if(reveal) PersianText(current.tr("translation", fa)) else PressTextButton({ reveal = true }) { Text(tr("نمایش معنی", "Reveal meaning")) }
                PersianText(current.tr("context", fa))
            } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PressOutlinedButton({ index-- }, enabled = index > 0) { Text(tr("قبلی", "Previous")) }
                PressOutlinedButton({ index++ }) { Text(tr("بعدی", "Next")) }
            }
        }
        Panel {
            Text(tr("${bookmarks.size} مورد نشان‌شده", "${bookmarks.size} saved items"), style = MaterialTheme.typography.titleLarge)
            if(bookmarks.isEmpty()) Text(tr("از صفحهٔ آهنگ، واژه‌ها و جمله‌های دلخواهت را نشان کن.", "Save your favourite words and lines from a song."))
            bookmarks.forEach { mark ->
                val item = savedLessons[mark.songId]?.a(mark.itemType)?.find { it.s(if(mark.itemType == "vocabulary") "lexemeId" else "id") == mark.itemId }
                Text(if(mark.itemType == "song") catalog.find { it.s("id") == mark.songId }?.s("title") ?: mark.songId else item?.let { it.s("lemma").ifEmpty { it.s("text").ifEmpty { it.tr("title", fa) } } } ?: mark.itemId)
                if(item == null && mark.itemType != "song") Text(tr("در محتوای قبلی؛ پیشرفت حفظ شده", "From previous content; progress preserved"), style = MaterialTheme.typography.bodySmall)
                PressTextButton({ model.toggleBookmark(mark.itemType, mark.itemId, mark.songId) }) { Text(tr("حذف نشان", "Remove bookmark")) }
            }
        }
        val installedSongs = catalog.filter { it.s("id") in installedIds && it.s("language") == settings.targetLanguage }
        if(installedSongs.isEmpty()) Panel {
            Text(tr("هنوز آهنگی نصب نشده است.", "No songs installed yet."))
        }
        installedSongs.forEach { entry -> Panel {
            Text(entry.s("title"), style = MaterialTheme.typography.titleLarge)
            Text(entry.s("artist"))
            Text(tr("آمادهٔ آفلاین", "Ready offline"))
            Action(tr("باز کردن آهنگ", "Open song"), onClick = { song(entry.s("id")) })
        } }
    }
}
