package com.hydro.reedance.domain

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.hydro.reedance.data.*
import java.text.Normalizer

/** Do not pretend an inflected/missing lemma was removed from its example. */
fun reviewQuestion(word: JsonObject, vocabulary: List<JsonObject>, index: Int): JsonObject? {
    val id = word.s("lexemeId")
    val lemma = Normalizer.normalize(word.s("lemma").trim(), Normalizer.Form.NFC)
    val meaning = word.s("translationFa").trim()
    if(id.isBlank() || lemma.isBlank() || meaning.isBlank()) return null
    val example = word.obj("example")
    val sentence = Normalizer.normalize(example.s("text"), Normalizer.Form.NFC)
    val pattern = Regex("(?<![\\p{L}\\p{M}\\p{N}_])${Regex.escape(lemma)}(?![\\p{L}\\p{M}\\p{N}_])", RegexOption.IGNORE_CASE)
    val occurrences = pattern.findAll(sentence).toList()
    val cloze = occurrences.singleOrNull()?.takeIf { !sentence.contains(Regex("_{2,}")) && example.s("translationFa").isNotBlank() }
    val distractors = vocabulary.filter { it.s("lexemeId").isNotBlank() && it.s("lemma").isNotBlank() &&
        it.s("lexemeId") != id && normalizeAnswer(it.s("lemma")) != normalizeAnswer(lemma) && it.s("translationFa").isNotBlank() &&
        normalizeAnswer(it.s("translationFa"), "fa") != normalizeAnswer(meaning, "fa") }
        .distinctBy { normalizeAnswer(it.s("translationFa"), "fa") }.distinctBy { it.s("lexemeId") }.take(3)
    val choice = index % 2 == 0 && distractors.isNotEmpty()
    return JsonObject().apply {
        addProperty("id", "review-$id")
        addProperty("type", if(choice) "choice" else "typing")
        addProperty("promptFa", if(choice) "معنی «$lemma» چیست؟" else if(cloze != null) "واژهٔ جاافتاده را بنویس." else "واژهٔ مربوط به این معنی را بنویس.")
        addProperty("skillFa", if(choice) "معنی واژه" else "یادآوری واژه")
        addProperty("explanationFa", "$lemma : $meaning")
        add("relatedLexemeIds", JsonArray().apply { add(id) })
        if(choice) {
            addProperty("correctId", id)
            add("options", JsonArray().apply {
                (listOf(word) + distractors).shuffled(java.util.Random(index.toLong())).forEach { v ->
                    add(JsonObject().apply { addProperty("id", v.s("lexemeId")); addProperty("fa", v.s("translationFa")) })
                }
            })
        } else {
            if(cloze != null) {
                addProperty("context", sentence.replaceRange(cloze.range, "____"))
                addProperty("translationFa", example.s("translationFa"))
            } else {
                // Recall from the Persian meaning, with no complete sentence leaking the answer.
                addProperty("translationFa", meaning)
            }
            add("accepted", JsonArray().apply { add(if(cloze != null) cloze.value else lemma) })
        }
    }
}

/** Structural checks only: linguistic correctness still depends on the authored package. */
fun usablePracticeQuestion(q: JsonObject): Boolean {
    if(q.s("id").isBlank() || q.s("promptFa").isBlank()) return false
    fun validIds(rows: List<JsonObject>) = rows.all { it.s("id").isNotBlank() } && rows.map { it.s("id") }.distinct().size == rows.size
    return when(q.s("type")) {
        "choice" -> {
            val options = q.a("options")
            val correct = options.singleOrNull { it.s("id") == q.s("correctId") }
            options.size >= 2 && validIds(options) && options.all { it.s("fa").isNotBlank() } && correct != null &&
                options.count { normalizeAnswer(it.s("fa"), "fa") == normalizeAnswer(correct.s("fa"), "fa") } == 1
        }
        "typing" -> q.strings("accepted").isNotEmpty() && q.strings("accepted").all { it.isNotBlank() } &&
            (q.s("context").isBlank() && q.s("translationFa").isNotBlank() ||
                Regex("_{2,}").findAll(q.s("context")).count() == 1)
        "order" -> {
            val chunks = q.a("chunks")
            val order = q.strings("presentationOrder")
            chunks.size >= 2 && validIds(chunks) && chunks.all { it.s("text").isNotBlank() } &&
                (order.isEmpty() || order.size == chunks.size && order.toSet() == chunks.map { it.s("id") }.toSet())
        }
        "match" -> {
            val pairs = q.a("pairs")
            val order = q.strings("meaningOrder")
            pairs.size >= 2 && validIds(pairs) && pairs.all { it.s("text").isNotBlank() && it.s("fa").isNotBlank() } &&
                (order.isEmpty() || order.size == pairs.size && order.toSet() == pairs.map { it.s("id") }.toSet())
        }
        else -> false
    }
}
