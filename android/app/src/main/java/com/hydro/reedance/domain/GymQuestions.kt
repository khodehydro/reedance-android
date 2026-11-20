package com.hydro.reedance.domain

import com.google.gson.JsonObject
import com.hydro.reedance.data.s
import kotlin.random.Random

internal data class PracticeKey(val songId: String, val questionId: String)
internal data class GymQuestion(val question: JsonObject, val source: JsonObject) {
    val key = PracticeKey(source.s("songId"), question.s("id"))
}

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
