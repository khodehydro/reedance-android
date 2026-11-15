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
private fun reviewQuestion(word: JsonObject, vocabulary: List<JsonObject>, index: Int): JsonObject = JsonObject().apply {
    addProperty("id", "review-${word.s("lexemeId")}")
    addProperty("type", if(index % 2 == 0) "choice" else "typing")
    addProperty("promptFa", if(index % 2 == 0) "معنی ${word.s("lemma")} چیست؟" else "واژهٔ جاافتاده را بنویس.")
    addProperty("promptEn", if(index % 2 == 0) "What does ${word.s("lemma")} mean?" else "Type the missing word.")
    addProperty("context", word.obj("example").s("text").replace(Regex("\\b${Regex.escape(word.s("lemma"))}\\b", RegexOption.IGNORE_CASE), "____"))
    addProperty("explanationFa", "${word.s("lemma")} : ${word.s("translationFa")}")
    addProperty("explanationEn", "${word.s("lemma")} : ${word.s("translationEn")}")
    addProperty("correctId", word.s("lexemeId"))
    add("relatedLexemeIds", JsonArray().apply { add(word.s("lexemeId")) })
    add("accepted", JsonArray().apply { add(word.s("lemma")) })
    add("options", JsonArray().apply {
        (listOf(word) + vocabulary.filter { it.s("lexemeId") != word.s("lexemeId") }.take(3)).shuffled(java.util.Random(index.toLong())).forEach { v ->
            add(JsonObject().apply { addProperty("id", v.s("lexemeId")); addProperty("fa", v.s("translationFa")); addProperty("en", v.s("translationEn")) })
        }
    })
}
private data class QuizSessionData(val dueCards: Map<String, ReviewCard>, val questions: List<JsonObject>)
@Composable fun QuizScreen(model: AppModel, review: Boolean, done: () -> Unit, openReview: () -> Unit) {
    val lesson by model.lesson.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val data = lesson?.takeIf { it.s("targetLanguage") == settings.targetLanguage }
    if(data == null) { Column(Modifier.padding(24.dp)) { Text(tr("ابتدا بستهٔ آهنگ را نصب کن.", "Install a song lesson first.")); PressTextButton(done) { Text(tr("بازگشت", "Back")) } }; return }
    val session by produceState<QuizSessionData?>(null, data, review) {
        val due = if(review) model.dueReviewCards() else emptyList()
        value = withContext(Dispatchers.Default) {
            val words = data.a("vocabulary")
            val dueMap = due.associateBy { it.lexemeId }
            QuizSessionData(dueMap, if(review) words.filter { it.s("lexemeId") in dueMap }.mapIndexed { i, word -> reviewQuestion(word, words, i) } else data.a("questions"))
        }
    }
    val prepared = session
    if(prepared == null) { Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }; return }
    val dueCards = prepared.dueCards
    val questions = prepared.questions
    var index by remember(data, review) { mutableIntStateOf(0) }
    var answer by remember(data, review, index) { mutableStateOf<List<String>>(emptyList()) }
    var checked by remember(data, review, index) { mutableStateOf<Grade?>(null) }
    var grades by remember(data, review) { mutableStateOf<List<Triple<JsonObject, List<String>, Grade>>>(emptyList()) }
    var busy by remember(data, review) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current
    val fa = LocalFa.current
    // This effect is cancelled when leaving the screen or changing the question/session.
    LaunchedEffect(index, checked, data, review) {
        val result = checked ?: return@LaunchedEffect
        if(result.correct && index < questions.size && questions[index].s("type") != "match") {
            delay(850) // Keep success visible, including when decorative motion is disabled.
            grades = grades + Triple(questions[index], answer.toList(), result)
            index++
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if(index >= questions.size) {
            Heading(if(questions.isEmpty()) tr("فعلاً مروری نداری", "All caught up") else tr("یک قدم جلوتر!", "One step further!"), tr("پیشرفتت روی همین دستگاه ذخیره شد.", "Your progress is saved on this device."))
            Text("${grades.count { it.third.correct }} / ${questions.size}", style = MaterialTheme.typography.headlineLarge)
            grades.forEach { (q,userAnswer,g) -> Panel {
                Text(q.tr("prompt", fa), style = MaterialTheme.typography.titleMedium)
                Text(if(g.correct) tr("✓ درست", "✓ Correct") else tr("نیاز به مرور", "Needs review"))
                Text(tr("پاسخ تو", "Your answer"), style = MaterialTheme.typography.labelMedium)
                Text(formattedAnswer(q, userAnswer, fa))
                Text(tr("پاسخ درست", "Correct answer"), style = MaterialTheme.typography.labelMedium)
                if(q.s("type") == "match") MatchBoard(q, correctAnswer(q), true, false, onAnswer = {}, solution = true)
                else Text(formattedAnswer(q, correctAnswer(q), fa))
                Text(q.tr("explanation", fa))
            } }
            Action(tr("بازگشت", "Back"), onClick = done)
            if(!review) PressOutlinedButton(openReview) { Text(tr("مرور واژه‌ها در لایتنر", "Review words in Leitner")) }
        } else {
            val q = questions[index]
            val submit: (List<String>) -> Unit = { submitted ->
                if(!busy && checked == null && complete(q, submitted)) {
                    busy = true
                    answer = submitted
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.Default) { grade(q, submitted, data.s("locale")) }
                            if(review) q.strings("relatedLexemeIds").forEach { dueCards[it]?.let { card -> model.review(card, result.correct) } }
                            else model.record(q, submitted, result)
                            checked = result
                        } finally { busy = false }
                    }
                }
            }
            Text(tr("سؤال ${index+1} از ${questions.size}", "Question ${index+1} of ${questions.size}"), color = MaterialTheme.colorScheme.primary)
            LinearProgressIndicator(progress = { index.toFloat()/questions.size }, modifier = Modifier.fillMaxWidth())
            Deck(index) {
                Panel {
                    Text(q.tr("skill", fa), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(q.tr("prompt", fa), style = MaterialTheme.typography.titleLarge)
                    if(q.s("context").isNotEmpty()) Foreign(q.s("context"))
                    when(q.s("type")) {
                        "choice" -> PracticeChoices(q, answer.singleOrNull(), checked, busy) { submit(listOf(it)) }
                        "typing" -> OutlinedTextField(answer.firstOrNull() ?: "", { answer = listOf(it) }, Modifier.fillMaxWidth(), enabled = checked == null && !busy, label = { Text(tr("پاسخ", "Answer")) }, singleLine = true)
                        "order" -> {
                            Foreign(answer.joinToString(" ") { id -> q.a("chunks").find { it.s("id") == id }?.s("text") ?: "" }, large = true)
                            val order = q.strings("presentationOrder").ifEmpty { q.a("chunks").map { it.s("id") }.reversed() }
                            order.forEach { id -> PressOutlinedButton({ answer = answer + id }, enabled = checked == null && !busy && id !in answer) { Text(q.a("chunks").first { it.s("id") == id }.s("text")) } }
                            PressTextButton({ answer = emptyList() }, enabled = checked == null && !busy) { Text(tr("از نو بچین", "Reset order")) }
                        }
                        "match" -> MatchBoard(q, answer, checked != null, !busy, onAnswer = { answer = it })

                    }
                    if(checked == null) {
                        if(q.s("type") != "choice") Action(tr("بررسی پاسخ", "Check answer"), enabled = complete(q, answer) && !busy) {
                            submit(answer.toList())
                        }
                    } else {
                        Text(if(checked!!.correct) tr("✓ آفرین، درست بود", "✓ You got it") else tr("نیاز به مرور؛ اشکالی ندارد", "Needs review — keep going"), style = MaterialTheme.typography.titleMedium)
                        Text(q.tr("explanation", fa))
                        if(q.s("type") == "match") {
                            Text(tr("پاسخ صحیح", "Correct matches"), style = MaterialTheme.typography.titleMedium)
                            MatchBoard(q, correctAnswer(q), true, false, onAnswer = {}, solution = true)
                        }
                        if(!checked!!.correct || q.s("type") == "match") Action(tr("ادامه", "Continue"), enabled = !busy) {
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
