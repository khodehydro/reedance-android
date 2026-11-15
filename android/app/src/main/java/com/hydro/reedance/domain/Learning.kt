package com.hydro.reedance.domain

import java.text.Normalizer
import java.util.Locale
import com.google.gson.JsonObject
import com.hydro.reedance.data.*

data class Schedule(val stage: Int, val dueAt: Long)
fun schedule(stage: Int, remembered: Boolean, now: Long): Schedule {
    val next = if (remembered) (stage + 1).coerceIn(2, 5) else 1
    val delay = if (remembered) longArrayOf(0, 1, 2, 4, 8, 16)[next] * 86_400_000 else 60_000
    return Schedule(next, now + delay)
}
fun normalizeAnswer(value: String, locale: String = "en-GB"): String =
    Normalizer.normalize(value, Normalizer.Form.NFC).trim().lowercase(Locale.forLanguageTag(locale))
        .replace(Regex("[.!?,…]+$"), "").trimEnd()

/** Compare the text the learner can see, not hidden translations or an arbitrary duplicate ID. */
private fun normalizedMeaning(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFC).trim().replace(Regex("[\\s\\p{Z}]+"), " ").lowercase(Locale.ROOT)

fun matchCorrectness(q: JsonObject, answer: List<String>, meaningLanguage: String = "en"): List<Boolean> {
    val pairs = q.a("pairs")
    val byId = pairs.associateBy { it.s("id") }
    val counts = answer.groupingBy { it }.eachCount()
    return pairs.mapIndexed { index, expected ->
        val chosenId = answer.getOrNull(index).orEmpty()
        val chosen = byId[chosenId]
        val expectedText = normalizedMeaning(expected.s(meaningLanguage))
        chosen != null && counts[chosenId] == 1 && (chosenId == expected.s("id") ||
            (expectedText.isNotBlank() && expectedText == normalizedMeaning(chosen.s(meaningLanguage))))
    }
}

data class Grade(val correct: Boolean, val missed: Set<String>)
fun grade(q: JsonObject, answer: List<String>, locale: String = "en-GB", meaningLanguage: String = "en"): Grade {
    val pairs = q.a("pairs")
    val matches = if(q.s("type") == "match") matchCorrectness(q, answer, meaningLanguage) else emptyList()
    val correct = when(q.s("type")) {
        "choice" -> answer.singleOrNull() == q.s("correctId")
        "typing" -> answer.singleOrNull()?.let { a -> q.strings("accepted").any { normalizeAnswer(a, locale) == normalizeAnswer(it, locale) } } == true
        "order" -> answer == q.a("chunks").map { it.s("id") }
        "match" -> complete(q, answer) && matches.all { it }
        else -> false
    }
    val missed = if(correct) emptySet() else if(q.s("type") == "match") pairs.filterIndexed { i, _ -> !matches[i] }.map { it.s("lexemeId") }.filter { it.isNotEmpty() }.toSet() else q.strings("relatedLexemeIds").toSet()
    return Grade(correct, missed)
}
fun complete(q: JsonObject, answer: List<String>) = when(q.s("type")) {
    "choice", "typing" -> answer.singleOrNull()?.isNotBlank() == true
    "order" -> answer.size == q.a("chunks").size
    "match" -> answer.size == q.a("pairs").size && answer.all { it.isNotBlank() } && answer.distinct().size == answer.size
    else -> false
}
