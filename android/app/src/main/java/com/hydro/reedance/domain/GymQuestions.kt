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

/** Pick fresh questions first, then recycle the least recently answered ones. */
internal fun selectGymRound(
    candidates: List<GymQuestion>, history: Map<PracticeKey, Long>, count: Int,
    random: Random = Random.Default
): List<GymQuestion> {
    val groups = candidates.distinctBy { it.key }.groupBy { history[it.key] }
    return buildList {
        for (time in groups.keys.sortedWith(compareBy { it })) {
            val songs = groups.getValue(time).groupBy { it.key.songId }
                .mapValues { (_, entries) -> entries.shuffled(random).toMutableList() }.toMutableMap()
            while (songs.isNotEmpty() && size < count) {
                val choices = songs.keys.filter { it != lastOrNull()?.key?.songId }.ifEmpty { songs.keys.toList() }
                val song = choices.random(random)
                val questions = songs.getValue(song)
                add(questions.removeAt(questions.lastIndex))
                if (questions.isEmpty()) songs.remove(song)
            }
            if (size >= count) break
        }
    }
}
