package com.hydro.reedance.domain

import com.google.gson.JsonObject
import com.hydro.reedance.data.*

internal data class CardClip(val startMs: Long, val endMs: Long)
internal data class ClipUnit(val id: String, val startMs: Long?, val endMs: Long?)
internal data class SongCardClips(val playable: List<CardClip>, val locked: Boolean)

internal fun groupCardClips(units: List<ClipUnit>, ids: Collection<String>, limit: Int = Int.MAX_VALUE): List<CardClip> {
    val linked = ids.toHashSet()
    val result = mutableListOf<CardClip>()
    var previous = -2
    units.take(limit).forEachIndexed { index, unit ->
        val start = unit.startMs ?: return@forEachIndexed
        val end = unit.endMs ?: return@forEachIndexed
        if(unit.id !in linked || start < 0 || end <= start) return@forEachIndexed
        if(index == previous + 1) {
            val last = result.last()
            result[result.lastIndex] = CardClip(minOf(last.startMs, start), maxOf(last.endMs, end))
        } else result.add(CardClip(start, end))
        previous = index
    }
    return result
}

internal fun JsonObject.isForeignLyric(line: JsonObject) =
    alternateLyricLanguage(s("targetLanguage"), line.s("language")) != null

internal fun JsonObject.foreignLyricIds() = a("lyrics").filter { isForeignLyric(it) }.map { it.s("id") }.toSet()

internal fun JsonObject.foreignOnlyLexemes(): Set<String> {
    val (foreign, native) = a("lyrics").partition { isForeignLyric(it) }
    fun ids(lines: List<JsonObject>) = lines.flatMap { it.a("tokens") }.map { it.s("lexemeId") }.toSet()
    return ids(foreign) - ids(native) - ""
}

/** Keep standalone vocabulary and words also used in a target-language line. */
internal fun JsonObject.teachingVocabulary(): List<JsonObject> {
    val foreignOnly = foreignOnlyLexemes()
    return a("vocabulary").filterNot { it.s("lexemeId") in foreignOnly }
}

internal fun songCardClips(lesson: JsonObject, lineIds: Collection<String>, access: LessonAccess = LessonAccess(false)): SongCardClips {
    val units = lesson.a("lyrics").map { line ->
        if(lesson.isForeignLyric(line)) ClipUnit(line.s("id"), null, null)
        else ClipUnit(line.s("id"), line.n("startMs", -1), line.n("endMs", -1))
    }
    val limit = access.limit("lyrics")
    // A hand-authored end can overlap a locked line. Never play it past that boundary.
    val firstLockedStart = lesson.a("lyrics").drop(limit).map { it.n("startMs", -1) }
        .filter { it >= 0 }.minOrNull()
    val previewUnits = if(firstLockedStart == null) units else units.map { unit ->
        if(unit.endMs != null && unit.endMs > firstLockedStart) unit.copy(endMs = null) else unit
    }
    val playable = groupCardClips(previewUnits, lineIds, limit)
    val locked = access.preview && playable.isEmpty() && groupCardClips(units, lineIds).isNotEmpty()
    return SongCardClips(playable, locked)
}

internal fun songWordLineIds(lesson: JsonObject, word: JsonObject): List<String> {
    val id = word.s("lexemeId")
    if(id.isBlank() || lesson.a("vocabulary").none { it.s("lexemeId") == id }) return emptyList()
    return lesson.a("lyrics").filter { !lesson.isForeignLyric(it) && it.a("tokens").any { token -> token.s("lexemeId") == id } }
        .map { it.s("id") }
}
