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
    val lesson by model.lesson.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while(true) { delay(1000); now = System.currentTimeMillis() } }
    val due = reviews.filter { it.dueAt <= now }
    val card = due.firstOrNull()
    val word = lesson?.a("vocabulary")?.find { it.s("lexemeId") == card?.lexemeId }
    var reveal by remember(card?.lexemeId) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current
    val fa = LocalFa.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp + LocalOverlayBottomInset.current), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Heading(tr("جعبهٔ لایتنر", "Words that stay"), tr("یادگیری، یک مرور کوتاه در هر روز.", "A little practice. A lasting memory."))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { (1..5).forEach { stage -> Column {
            Text("$stage", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text("${reviews.count { it.stage == stage }}", style = MaterialTheme.typography.bodySmall)
        } } }
        Text(tr("${due.size} واژه آمادهٔ مرور", "${due.size} words ready to review"))
        if(card != null && word != null) {
            Deck(card.lexemeId) {
                Panel(tone = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(tr("کارت واژه · خانهٔ ${card.stage}", "WORD CARD · STAGE ${card.stage}"), style = MaterialTheme.typography.labelMedium)
                    Foreign(word.s("lemma"), large = true)
                    Foreign(word.s("ipa"))
                    if(fa) Text(word.s("faReading"))
                    if(!reveal) Action(tr("برگرداندن کارت", "Reveal meaning")) { reveal = true }
                    else {
                        Text(word.tr("translation", fa), style = MaterialTheme.typography.titleLarge)
                        Foreign(word.obj("example").s("text")); Text(word.obj("example").tr("translation", fa))
                        Action(tr("یادم بود", "Remembered"), enabled = !busy) {
                            if(!busy) { busy = true; scope.launch { model.review(card, true); if(!reduced) delay(220); busy = false } }
                        }
                        PressOutlinedButton({ if(!busy) { busy = true; scope.launch { model.review(card, false); if(!reduced) delay(220); busy = false } } }, enabled = !busy) { Text(tr("دوباره مرور کنم", "Review again")) }
                    }
                }
            }
            PressOutlinedButton(quiz, Modifier.fillMaxWidth()) { Text(tr("مرور با سؤال", "Practice with questions")) }
        } else Panel {
            Text(if(reviews.isEmpty()) tr("در تب لغت‌های آهنگ، واژه‌ای را نشان کن تا به جعبه‌ات بیاید.", "Save a word in the song lesson to start your collection.") else if(lesson == null) tr("برای نمایش کارت‌ها، بستهٔ آهنگ را دوباره نصب کن. پیشرفتت حفظ شده است.", "Reinstall the song to view these cards. Your progress is preserved.") else tr("عالی بود! واژه‌ها در موعد بعدی برمی‌گردند.", "All caught up! Your words will return when they are due."))
        }
        Text(tr("پاسخ درست: خانهٔ بعد با فاصلهٔ ۲، ۴، ۸ و ۱۶ روز. فراموشی: خانهٔ اول، یک دقیقهٔ دیگر.", "Remembered: next stage in 2, 4, 8 or 16 days. Forgotten: stage one, due in one minute."), style = MaterialTheme.typography.bodySmall)
    }
}
@Composable fun LibraryScreen(model: AppModel, song: () -> Unit) {
    val bookmarks by model.bookmarks.collectAsStateWithLifecycle()
    val lesson by model.lesson.collectAsStateWithLifecycle()
    var index by remember { mutableIntStateOf(0) }
    var reveal by remember(index) { mutableStateOf(false) }
    val fa = LocalFa.current
    val savedPhrases = bookmarks.filter { it.itemType in listOf("idioms", "sentences") }.mapNotNull { mark -> lesson?.a(mark.itemType)?.find { it.s("id") == mark.itemId } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp + LocalOverlayBottomInset.current), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Heading(tr("کتابخانهٔ تو", "Your little collection"), tr("چیزهایی که می‌خواهی همراهت بمانند.", "Keep the words and lines that speak to you."))
        if(savedPhrases.isNotEmpty()) {
            val current = savedPhrases[index.mod(savedPhrases.size)]
            Deck(index) { Panel(tone = MaterialTheme.colorScheme.secondaryContainer) {
                Text(tr("عبارت نشان‌شده", "SAVED LINE"), style = MaterialTheme.typography.labelMedium)
                Foreign(current.s("text"), large = true)
                if(reveal) Text(current.tr("translation", fa)) else PressTextButton({ reveal = true }) { Text(tr("نمایش معنی", "Reveal meaning")) }
                Text(current.tr("context", fa))
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
                val item = lesson?.a(mark.itemType)?.find { it.s(if(mark.itemType == "vocabulary") "lexemeId" else "id") == mark.itemId }
                Text(item?.let { it.s("lemma").ifEmpty { it.s("text").ifEmpty { it.tr("title", fa) } } } ?: mark.itemId)
                if(item == null && mark.itemType != "song") Text(tr("در محتوای قبلی؛ پیشرفت حفظ شده", "From previous content; progress preserved"), style = MaterialTheme.typography.bodySmall)
                PressTextButton({ model.toggleBookmark(mark.itemType, mark.itemId) }) { Text(tr("حذف نشان", "Remove bookmark")) }
            }
        }
        Panel {
            Text("Midnight Avenue", style = MaterialTheme.typography.titleLarge)
            Text(if(lesson != null) tr("آمادهٔ آفلاین · دموی محلی", "Ready offline · local demo") else tr("نصب نشده", "Not installed"))
            Action(tr("باز کردن آهنگ", "Open song"), onClick = song)
        }
    }
}
