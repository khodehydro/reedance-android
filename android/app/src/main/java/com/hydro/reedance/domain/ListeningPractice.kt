package com.hydro.reedance.domain

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.hydro.reedance.data.*
import kotlin.random.Random

internal data class ListeningClip(val startMs: Long, val endMs: Long, val distractors: Map<String, String>)

private val spacing = Regex("[\\s\\p{Z}]+")
private val edgePunctuation = Regex("^[\\p{P}]+|[\\p{P}]+$")
private fun words(text: String) = text.trim().split(spacing).filter { it.isNotBlank() }
private fun wordKey(text: String, locale: String) = normalizeAnswer(text.replace(edgePunctuation, ""), locale)

/** Derived from the installed lesson only; authored questions and their keys are untouched. */
internal fun prepareListeningQuestions(source: JsonObject): List<GymQuestion> {
    val audio = source.obj("media").obj("audio")
    val duration = audio.n("durationMs")
    if (audio.s("path").isBlank() || duration <= 0) return emptyList()
    val lines = source.a("lyrics")
    val locale = source.s("locale", "en-GB")
    val authored = source.a("questions")
    val authoredIds = authored.mapTo(hashSetOf()) { it.s("id") }
    val authoredChunks = authored.filter { it.s("type") == "order" }.groupBy { it.s("lineId") }
    val pool = (lines + source.a("sentences") + source.a("idioms") + source.a("vocabulary").map { it.obj("example") })
        .flatMap { words(it.s("text")) }.distinctBy { wordKey(it, locale) }.filter { wordKey(it, locale).isNotBlank() }
    return lines.mapIndexedNotNull { index, line ->
        val lineId = line.s("id")
        val start = line.n("startMs", -1)
        val boundary = lines.getOrNull(index + 1)?.n("startMs", duration) ?: duration
        val end = minOf(line.n("endMs", boundary), boundary, duration)
        val sentence = words(line.s("text"))
        if (lineId.isBlank() || start < 0 || end <= start || sentence.isEmpty()) return@mapIndexedNotNull null
        val correct = authoredChunks[lineId].orEmpty().asSequence().map { q -> q.a("chunks").map { it.s("text") } }
            .firstOrNull { it.size in 2..12 && it.all(String::isNotBlank) && words(it.joinToString(" ")) == sentence }
            ?: sentence.chunked(((sentence.size + 9) / 10).coerceAtLeast(1)).map { it.joinToString(" ") }
        val excluded = sentence.mapTo(hashSetOf()) { wordKey(it, locale) }
        val offset = if (pool.isEmpty()) 0 else Random(lineId.hashCode()).nextInt(pool.size)
        val extras = (pool.indices).asSequence().map { pool[(offset + it) % pool.size] }
            .filter { wordKey(it, locale) !in excluded }.take(2).toList()
        if (extras.isEmpty()) return@mapIndexedNotNull null
        var id = "listen:$lineId"
        while (id in authoredIds) id = "listen:$id"
        val q = JsonObject().apply {
            addProperty("id", id)
            addProperty("type", "order")
            addProperty("skillFa", "تمرین شنیداری")
            addProperty("skillEn", "Listening practice")
            addProperty("promptFa", "به آهنگ گوش کن و جمله را با قطعه‌های درست بساز.")
            addProperty("promptEn", "Listen to the song and build the sentence with the right pieces.")
            addProperty("explanationFa", line.s("translationFa"))
            add("chunks", JsonArray().apply {
                correct.forEachIndexed { i, text -> add(JsonObject().apply {
                    addProperty("id", "part-$i")
                    addProperty("text", text)
                }) }
            })
        }
        GymQuestion(q, source, listening = ListeningClip(start, end, extras.mapIndexed { i, text -> "extra-$i" to text }.toMap()))
    }
}

/** Identical visible pieces are interchangeable, but extra choices are never in the key. */
internal fun gradeListening(question: JsonObject, answer: List<String>): Grade {
    val chunks = question.a("chunks")
    val byId = chunks.associate { it.s("id") to it.s("text") }
    val correct = answer.size == chunks.size && answer.distinct().size == answer.size &&
        answer.map { byId[it] } == chunks.map { it.s("text") }
    return Grade(correct, emptySet())
}
