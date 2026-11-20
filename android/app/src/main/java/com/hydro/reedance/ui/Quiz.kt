package com.hydro.reedance.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.*
import com.hydro.reedance.data.*
import com.hydro.reedance.domain.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.*

private fun formattedAnswer(q: JsonObject, answer: List<String>, fa: Boolean): String = when(q.s("type")) {
    "choice" -> q.a("options").find { it.s("id") == answer.firstOrNull() }?.s(if(fa) "fa" else "en") ?: "—"
    "order" -> answer.joinToString(" ") { id -> q.a("chunks").find { it.s("id") == id }?.s("text") ?: "—" }
    "match" -> q.a("pairs").mapIndexed { i, pair -> pair.s("text") + " → " + (q.a("pairs").find { it.s("id") == answer.getOrNull(i) }?.s(if(fa) "fa" else "en") ?: "—") }.joinToString("\n")
    else -> answer.joinToString(" ")
}
private fun correctAnswer(q: JsonObject): List<String> = when(q.s("type")) {
    "choice" -> listOf(q.s("correctId"))
    "order" -> q.a("chunks").map { it.s("id") }
    "match" -> q.a("pairs").map { it.s("id") }
    else -> listOf(q.strings("accepted").firstOrNull() ?: "")
}
private data class QuizSessionData(val questions: List<GymQuestion>, val skipped: Int, val source: JsonObject)

@Composable fun QuizScreen(model: AppModel, done: () -> Unit, openReview: () -> Unit) {
    val lesson by model.lesson.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val data = lesson?.takeIf { it.s("targetLanguage") == settings.targetLanguage }
    if(data == null) { Column(Modifier.padding(24.dp)) { Text(tr("ابتدا بستهٔ آهنگ را نصب کن.", "Install a song lesson first.")); PressTextButton(done) { Text(tr("بازگشت", "Back")) } }; return }
    val session by produceState<QuizSessionData?>(null, data) {
        value = withContext(Dispatchers.Default) {
            val source = data.a("questions")
            val questions = source.filter(::usablePracticeQuestion).distinctBy { it.s("id") }.map { GymQuestion(it, data) }
            QuizSessionData(questions, source.size - questions.size, data)
        }
    }
    val prepared = session?.takeIf { it.source === data }
    if(prepared == null) { QuizLoading(); return }
    key(prepared) {
        PracticeSession(prepared.questions, prepared.skipped, onGrade = { entry, answer, result -> model.record(entry.question, answer, result) }) {
            Action(tr("بازگشت", "Back"), onClick = done)
            PressOutlinedButton(openReview) { Text(tr("مرور واژه‌ها در لایتنر", "Review words in Leitner")) }
        }
    }
}

@Composable private fun QuizLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
}

@Composable fun GymScreen(model: AppModel, explore: () -> Unit) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val installed by model.installedIds.collectAsStateWithLifecycle()
    val active = LocalPageActive.current
    var round by remember(settings.targetLanguage) { mutableIntStateOf(0) }
    var entries by remember(settings.targetLanguage, installed, round) { mutableStateOf<List<GymQuestion>?>(null) }
    var failed by remember(settings.targetLanguage, installed, round) { mutableStateOf(false) }
    LaunchedEffect(active, settings.targetLanguage, installed, round) {
        if(active && entries == null && !failed) {
            try { entries = model.gymRound(settings.targetLanguage) }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { failed = true }
        }
    }
    val current = entries
    when {
        failed || current?.isEmpty() == true -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Heading(tr("تمرین", "Gym"), if(failed) tr("بارگیری سؤال‌ها ممکن نبود.", "Could not load questions.") else
                tr("برای تمرین، یک بستهٔ آهنگِ دارای سؤال برای زبان فعلی نصب کن.", "Install a song lesson with questions for your current language to get started."))
            if(failed) Action(tr("تلاش دوباره", "Try again")) { round++ }
            else Action(tr("کشف آهنگ‌ها", "Explore songs"), onClick = explore)
        }
        current == null -> if(active) QuizLoading()
        else -> key(current) {
            PracticeSession(current, 0, gym = true, onGrade = model::recordGym) {
                Action(tr("دور بعد", "Next round")) { round++ }
            }
        }
    }
}

@Composable private fun PracticeSession(
    entries: List<GymQuestion>, skipped: Int, gym: Boolean = false,
    onGrade: suspend (GymQuestion, List<String>, Grade) -> Unit,
    finish: @Composable () -> Unit
) {
    val questions = entries.map { it.question }
    var index by remember { mutableIntStateOf(0) }
    var answer by remember(index) { mutableStateOf<List<String>>(emptyList()) }
    var checked by remember(index) { mutableStateOf<Grade?>(null) }
    var grades by remember { mutableStateOf<List<Triple<JsonObject, List<String>, Grade>>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var saveFailed by remember(index) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current
    val fa = LearningLanguage.persian
    // This effect is cancelled when leaving the screen or changing the question/session.
    LaunchedEffect(index, checked) {
        val result = checked ?: return@LaunchedEffect
        if(result.correct && index < questions.size) {
            delay(850) // Keep success visible, including when decorative motion is disabled.
            grades = grades + Triple(questions[index], answer.toList(), result)
            index++
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp + LocalOverlayBottomInset.current), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if(gym) Heading(tr("تمرین", "Gym"), tr("سؤال‌هایی از آهنگ‌های نصب‌شده", "Mixed questions from your installed songs"))
        if(skipped > 0) PersianText("$skipped سؤال ناقص یا ناسازگار از این تمرین کنار گذاشته شد؛ پیشرفت آن‌ها تغییر نکرد.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if(index >= questions.size) {
            Heading(if(questions.isEmpty() && skipped > 0) tr("سؤال معتبری در دسترس نیست", "No valid questions available") else if(questions.isEmpty()) tr("فعلاً مروری نداری", "All caught up") else tr("یک قدم جلوتر!", "One step further!"), tr("پیشرفتت روی همین دستگاه ذخیره شد.", "Your progress is saved on this device."))
            Text("${grades.count { it.third.correct }} / ${questions.size}", style = MaterialTheme.typography.headlineLarge)
            grades.forEach { (q,userAnswer,g) -> Panel {
                PersianText(q.tr("prompt", fa), style = MaterialTheme.typography.titleMedium)
                Text(if(g.correct) tr("✓ درست", "✓ Correct") else tr("نیاز به مرور", "Needs review"))
                Text(tr("پاسخ تو", "Your answer"), style = MaterialTheme.typography.labelMedium)
                if(q.s("type") == "choice" || q.s("type") == "match") PersianText(formattedAnswer(q, userAnswer, fa))
                else Foreign(formattedAnswer(q, userAnswer, fa))
                Text(tr("پاسخ درست", "Correct answer"), style = MaterialTheme.typography.labelMedium)
                if(q.s("type") == "match") MatchBoard(q, correctAnswer(q), true, false, onAnswer = {}, solution = true)
                else if(q.s("type") == "choice") PersianText(formattedAnswer(q, correctAnswer(q), fa))
                else Foreign(formattedAnswer(q, correctAnswer(q), fa))
                PersianText(q.tr("explanation", fa))
            } }
            finish()
        } else {
            val entry = entries[index]
            val q = entry.question
            val data = entry.source
            val submit: (List<String>) -> Unit = { submitted ->
                if(!busy && checked == null && complete(q, submitted)) {
                    busy = true
                    saveFailed = false
                    answer = submitted
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.Default) { grade(q, submitted, data.s("locale"), if(fa) "fa" else "en") }
                            onGrade(entry, submitted, result)
                            checked = result
                        } catch(cancelled: CancellationException) { throw cancelled }
                        catch(_: Exception) { saveFailed = true }
                        finally { busy = false }
                    }
                }
            }
            Text(tr("سؤال ${index+1} از ${questions.size}", "Question ${index+1} of ${questions.size}"), color = MaterialTheme.colorScheme.primary)
            if(gym) Text(data.s("title"), style = MaterialTheme.typography.labelLarge)
            if(saveFailed) Text(tr("پاسخ ذخیره نشد؛ دوباره امتحان کن.", "Could not save your answer. Try again."), color = MaterialTheme.colorScheme.error)
            LinearProgressIndicator(progress = { index.toFloat()/questions.size }, modifier = Modifier.fillMaxWidth())
            Deck(index) {
                Panel {
                    PersianText(q.tr("skill", fa), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    PersianText(q.tr("prompt", fa), style = MaterialTheme.typography.titleLarge)
                    if(q.s("context").isNotEmpty()) Foreign(q.s("context"))
                    if(q.s("type") == "typing") {
                        typingSentenceMeaning(q, data, fa).takeIf { it.isNotBlank() }?.let {
                            PersianText(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    when(q.s("type")) {
                        "choice" -> PracticeChoices(q, answer.singleOrNull(), checked, busy) { submit(listOf(it)) }
                        "typing" -> PracticeTypingField(answer.firstOrNull().orEmpty(), checked == null && !busy) { answer = listOf(it) }
                        "order" -> SentenceOrder(q, answer, checked, busy) { answer = it }
                        "match" -> MatchBoard(q, answer, checked != null, !busy, onAnswer = { answer = it })

                    }
                    if(checked == null) {
                        if(q.s("type") != "choice") Action(tr("بررسی پاسخ", "Check answer"), enabled = complete(q, answer) && !busy) {
                            submit(answer.toList())
                        }
                    } else {
                        Text(if(checked!!.correct) tr("✓ آفرین، درست بود", "✓ You got it") else tr("نیاز به مرور؛ اشکالی ندارد", "Needs review — keep going"), style = MaterialTheme.typography.titleMedium)
                        PersianText(q.tr("explanation", fa))
                        if(q.s("type") == "match" && !checked!!.correct) {
                            Text(tr("پاسخ صحیح", "Correct matches"), style = MaterialTheme.typography.titleMedium)
                            MatchBoard(q, correctAnswer(q), true, false, onAnswer = {}, solution = true)
                        }
                        if(!checked!!.correct && q.s("type") in listOf("order", "typing")) {
                            Text(tr("پاسخ صحیح", "Correct answer"), style = MaterialTheme.typography.labelLarge)
                            Foreign(formattedAnswer(q, correctAnswer(q), fa), large = true)
                        }
                        if(!checked!!.correct) Action(tr("ادامه", "Continue"), enabled = !busy) {
                            if(!busy) { busy = true; scope.launch {
                                if(!reduced) delay(220)
                                grades = grades + Triple(q, answer.toList(), checked!!)
                                index++; busy = false
                            } }
                        }
                    }
                }
            }
        }
    }
}
