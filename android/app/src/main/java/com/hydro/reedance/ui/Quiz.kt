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
import kotlinx.coroutines.launch

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
@Composable fun QuizScreen(model: AppModel, review: Boolean, done: () -> Unit, openReview: () -> Unit) {
    val lesson by model.lesson.collectAsStateWithLifecycle()
    val cards by model.reviews.collectAsStateWithLifecycle()
    val data = lesson
    if(data == null) { Column(Modifier.padding(24.dp)) { Text(tr("ابتدا بستهٔ دمو را نصب کن.", "Install the demo lesson first.")); TextButton(done) { Text(tr("بازگشت", "Back")) } }; return }
    val vocabulary = data.a("vocabulary")
    val dueCards = remember { cards.filter { it.dueAt <= System.currentTimeMillis() }.associateBy { it.lexemeId } }
    val questions = remember { if(review) vocabulary.filter { it.s("lexemeId") in dueCards }.mapIndexed { i,w -> reviewQuestion(w, vocabulary, i) } else data.a("questions") }
    var index by remember { mutableIntStateOf(0) }
    var answer by remember(index) { mutableStateOf<List<String>>(emptyList()) }
    var checked by remember(index) { mutableStateOf<Grade?>(null) }
    var grades by remember { mutableStateOf<List<Triple<JsonObject, List<String>, Grade>>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current
    val fa = LocalFa.current
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
                Text(formattedAnswer(q, correctAnswer(q), fa))
                Text(q.tr("explanation", fa))
            } }
            Action(tr("بازگشت", "Back"), onClick = done)
            if(!review) OutlinedButton(openReview) { Text(tr("مرور واژه‌ها در لایتنر", "Review words in Leitner")) }
        } else {
            val q = questions[index]
            Text(tr("سؤال ${index+1} از ${questions.size}", "Question ${index+1} of ${questions.size}"), color = MaterialTheme.colorScheme.primary)
            LinearProgressIndicator(progress = { index.toFloat()/questions.size }, modifier = Modifier.fillMaxWidth())
            Deck(index) {
                Panel {
                    Text(q.tr("skill", fa), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(q.tr("prompt", fa), style = MaterialTheme.typography.titleLarge)
                    if(q.s("context").isNotEmpty()) Foreign(q.s("context"))
                    when(q.s("type")) {
                        "choice" -> q.a("options").forEach { option ->
                            OutlinedButton({ answer = listOf(option.s("id")) }, Modifier.fillMaxWidth(), enabled = checked == null && !busy,
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = if(answer.singleOrNull() == option.s("id")) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
                                Text((if(answer.singleOrNull() == option.s("id")) "● " else "○ ") + option.s(if(fa) "fa" else "en"))
                            }
                        }
                        "typing" -> OutlinedTextField(answer.firstOrNull() ?: "", { answer = listOf(it) }, Modifier.fillMaxWidth(), enabled = checked == null && !busy, label = { Text(tr("پاسخ", "Answer")) }, singleLine = true)
                        "order" -> {
                            Foreign(answer.joinToString(" ") { id -> q.a("chunks").find { it.s("id") == id }?.s("text") ?: "" }, large = true)
                            val order = q.strings("presentationOrder").ifEmpty { q.a("chunks").map { it.s("id") }.reversed() }
                            order.forEach { id -> OutlinedButton({ answer = answer + id }, enabled = checked == null && !busy && id !in answer) { Text(q.a("chunks").first { it.s("id") == id }.s("text")) } }
                            TextButton({ answer = emptyList() }, enabled = checked == null && !busy) { Text(tr("از نو بچین", "Reset order")) }
                        }
                        "match" -> {
                            val pairs = q.a("pairs")
                            pairs.forEachIndexed { i, pair ->
                                Foreign(pair.s("text"))
                                val order = q.strings("meaningOrder").ifEmpty { pairs.map { it.s("id") }.reversed() }
                                order.forEach { id ->
                                    val meaning = pairs.first { it.s("id") == id }
                                    val selected = answer.getOrNull(i) == id
                                    FilterChip(selected, {
                                        val next = MutableList(pairs.size) { answer.getOrNull(it) ?: "" }
                                        next.indices.filter { it != i && next[it] == id }.forEach { next[it] = "" }
                                        next[i] = id; answer = next
                                    }, label = { Text(meaning.s(if(fa) "fa" else "en")) }, enabled = checked == null && !busy)
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                    if(q.s("speech").isNotEmpty()) TextButton({ model.speak(q.s("speech"), data.s("locale")) }) { Text(tr("شنیدن جمله با صدای دستگاه", "Hear sentence with device voice")) }
                    if(checked == null) Action(tr("بررسی پاسخ", "Check answer"), enabled = complete(q, answer) && !busy) {
                        val result = grade(q, answer, data.s("locale"))
                        checked = result
                        if(review) q.strings("relatedLexemeIds").forEach { dueCards[it]?.let { card -> model.review(card, result.correct) } }
                        else model.record(q, answer, result)
                    } else {
                        Text(if(checked!!.correct) tr("✓ آفرین، درست بود", "✓ You got it") else tr("نیاز به مرور؛ اشکالی ندارد", "Needs review — keep going"), style = MaterialTheme.typography.titleMedium)
                        Text(q.tr("explanation", fa))
                        Action(tr("ادامه", "Continue"), enabled = !busy) {
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
