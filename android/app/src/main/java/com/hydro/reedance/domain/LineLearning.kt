package com.hydro.reedance.domain

import com.google.gson.JsonObject
import com.hydro.reedance.data.*
import java.util.Locale

internal data class LineLearning(val grammar: List<JsonObject>, val phrases: List<Pair<String, JsonObject>>, val words: List<JsonObject>)

private val linePunctuation = Regex("[^\\p{L}\\p{M}\\p{N}]+")
private fun lineKey(text: String, locale: Locale) = linePunctuation.replace(text.lowercase(locale), " ").trim()

internal fun lineLearning(lesson: JsonObject, line: JsonObject, access: LessonAccess): LineLearning {
    if(lesson.isForeignLyric(line)) return LineLearning(emptyList(), emptyList(), emptyList())
    val locale = Locale.forLanguageTag(lesson.s("locale"))
    val text = lineKey(line.s("text"), locale)
    val id = line.s("id")
    fun matchesLine(example: String) = text.isNotEmpty() && lineKey(example, locale) == text
    val grammar = lesson.a("grammar").take(access.limit("grammar"))
        .filter { id in it.strings("lineIds") || matchesLine(it.obj("example").s("text")) }
    val phrases = listOf("idioms", "sentences").flatMap { type ->
        lesson.a(type).take(access.limit(type)).filter { phrase ->
            val reference = phrase.s("lineId")
            if(reference.isNotBlank()) reference == id else matchesLine(phrase.s("text"))
        }.map { type to it }
    }
    val vocabulary = lesson.teachingVocabulary().take(access.limit("vocabulary")).associateBy { it.s("lexemeId") }
    val words = line.a("tokens").mapNotNull { token ->
        val lexeme = token.s("lexemeId")
        vocabulary[lexeme] ?: if(access.preview && lexeme.isNotBlank()) null else JsonObject().apply {
            addProperty("lemma", token.s("surface"))
            addProperty("translationFa", token.s("translationFa"))
            addProperty("translationEn", token.s("translationEn"))
        }
    }.distinctBy { word -> word.s("lexemeId").ifBlank { lineKey(word.s("lemma"), locale) + ":" + word.s("translationFa") } }
    return LineLearning(grammar, phrases, words)
}
