package com.hydro.reedance.domain

import com.google.gson.JsonObject
import com.hydro.reedance.data.*
import kotlin.random.Random

internal data class PracticeKey(val songId: String, val questionId: String)
internal data class GymQuestion(val question: JsonObject, val source: JsonObject, val orderMeaningFa: String? = null, val listening: ListeningClip? = null) {
    val key = PracticeKey(source.s("songId"), question.s("id"))
}

/** Preserve the package's exact question and grading order. Never guess a Persian translation. */
internal fun preparePracticeQuestions(source: JsonObject): List<GymQuestion> = source.a("questions").mapNotNull { question ->
    if (!usablePracticeQuestion(question)) return@mapNotNull null
    val meaning = if (question.s("type") == "order") {
        orderSentenceMeaningFa(question, source).takeIf { it.isNotBlank() } ?: return@mapNotNull null
    } else null
    GymQuestion(question, source, meaning)
}.distinctBy { it.key }

internal fun orderSentenceMeaningFa(question: JsonObject, lesson: JsonObject): String {
    question.s("translationFa").trim().takeIf { it.isNotEmpty() }?.let { return it }
    val sentence = orderText(question.a("chunks").joinToString(" ") { it.s("text") })
    if (sentence.isBlank()) return ""
    val lyrics = lesson.a("lyrics")
    val lineId = question.s("lineId")
    val linked = lyrics.firstOrNull { lineId.isNotBlank() && it.s("id") == lineId }
    val candidates = listOfNotNull(linked) + lyrics + lesson.a("sentences") + lesson.a("idioms") +
        lesson.a("vocabulary").map { it.obj("example") }
    return candidates.firstOrNull { orderText(it.s("text")) == sentence && it.s("translationFa").isNotBlank() }
        ?.s("translationFa")?.trim().orEmpty()
}

private val orderWhitespace = Regex("[\\s\\p{Z}]+")
private fun orderText(text: String): String = normalizeAnswer(text.replace(orderWhitespace, " "))

private enum class GymQuestionType { LISTENING_ORIGINAL, LISTENING_PERSIAN, CHOICE, ORDER, MATCH, TYPING }

private fun GymQuestion.typeForRound(): GymQuestionType? = when {
    listening?.originalForMeaning != null -> GymQuestionType.LISTENING_PERSIAN
    listening != null -> GymQuestionType.LISTENING_ORIGINAL
    else -> when (question.s("type")) {
        "choice" -> GymQuestionType.CHOICE
        "order" -> GymQuestionType.ORDER
        "match" -> GymQuestionType.MATCH
        "typing" -> GymQuestionType.TYPING
        else -> null
    }
}

/** One question per available type, preferring unanswered questions and then the oldest answers. */
internal fun selectGymRound(
    candidates: List<GymQuestion>, history: Map<PracticeKey, Long>, random: Random = Random.Default
): List<GymQuestion> {
    val picked = candidates.distinctBy { it.key }.groupBy(GymQuestion::typeForRound)
        .filterKeys { it != null }.values.map { entries ->
            val oldest = entries.minOfWith(nullsFirst(naturalOrder<Long>())) { history[it.key] }
            entries.filter { history[it.key] == oldest }.random(random)
        }
    // Presentation has no fixed type sequence. Prefer switching songs where possible.
    val songs = picked.shuffled(random).groupBy { it.key.songId }
        .mapValues { (_, entries) -> entries.toMutableList() }.toMutableMap()
    return buildList {
        while (songs.isNotEmpty()) {
            val choices = songs.keys.filter { it != lastOrNull()?.key?.songId }.ifEmpty { songs.keys.toList() }
            val song = choices.random(random)
            val entries = songs.getValue(song)
            add(entries.removeAt(entries.lastIndex))
            if (entries.isEmpty()) songs.remove(song)
        }
    }
}
