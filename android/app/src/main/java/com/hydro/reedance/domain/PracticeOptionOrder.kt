package com.hydro.reedance.domain

import com.google.gson.JsonObject
import com.hydro.reedance.data.a
import com.hydro.reedance.data.s
import kotlin.random.Random

/** Shuffle display IDs only; the original question remains the grading source. */
internal fun practiceOptionOrder(question: JsonObject, random: Random = Random.Default): List<String> {
    val type = question.s("type")
    val key = when (type) {
        "choice" -> "options"
        "order" -> "chunks"
        "match" -> "pairs"
        else -> return emptyList()
    }
    val ids = question.a(key).map { it.s("id") }
    val shuffled = ids.shuffled(random)
    // Don't reveal a full solution via the initial order of chunks.
    return if (type == "order" && ids.size > 1 && shuffled == ids) ids.drop(1) + ids.first() else shuffled
}
