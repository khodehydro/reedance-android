package com.hydro.reedance

import com.google.gson.JsonParser
import com.hydro.reedance.domain.*
import com.hydro.reedance.data.*
import org.junit.Assert.*
import org.junit.Test

class CardClipsTest {
    private val lesson = JsonParser.parseString("""{
        "songId":"test","targetLanguage":"es","locale":"es-ES",
        "media":{"audio":{"path":"audio.wav","durationMs":900}},
        "lyrics":[
            {"id":"a","text":"buenos días","startMs":0,"endMs":90,"tokens":[{"lexemeId":"es:shared"}]},
            {"id":"b","text":"cómo estás","startMs":100,"endMs":190},
            {"id":"foreign","language":"en","text":"good morning","startMs":200,"endMs":290,
                "tokens":[{"lexemeId":"es:foreign"},{"lexemeId":"es:shared"}]},
            {"id":"c","text":"hasta luego","startMs":300,"endMs":390},
            {"id":"untimed","text":"adiós"},
            {"id":"invalid","startMs":500,"endMs":500},
            {"id":"d","text":"hasta mañana","startMs":600,"endMs":690}],
        "vocabulary":[{"lexemeId":"es:foreign"},{"lexemeId":"es:shared"},{"lexemeId":"es:standalone"}],
        "questions":[
            {"id":"foreign-q","type":"choice","lineId":"foreign","promptFa":"معنی؟","correctId":"x","options":[{"id":"x","fa":"بله"},{"id":"y","fa":"خیر"}]},
            {"id":"foreign-word-q","type":"choice","relatedLexemeIds":["es:foreign"],"promptFa":"معنی؟","correctId":"x","options":[{"id":"x","fa":"بله"},{"id":"y","fa":"خیر"}]},
            {"id":"native-q","type":"choice","lineId":"a","relatedLexemeIds":["es:shared"],"promptFa":"معنی؟","correctId":"x","options":[{"id":"x","fa":"بله"},{"id":"y","fa":"خیر"}]}]
    }""").asJsonObject

    @Test fun eligibleAdjacentLinesFormClipsAndInvalidReferencesBreakGroups() {
        val ids = listOf("d", "b", "a", "foreign", "c", "untimed", "invalid", "missing", "a")
        assertEquals(listOf(CardClip(0, 190), CardClip(300, 390), CardClip(600, 690)), songCardClips(lesson, ids).playable)
        assertEquals(SongCardClips(emptyList(), false), songCardClips(lesson, emptyList()))
        assertEquals(SongCardClips(emptyList(), false), songCardClips(lesson, listOf("foreign", "missing", "untimed", "invalid")))
    }

    @Test fun previewNeverIncludesLockedUnitsEvenWhenAdjacent() {
        val access = LessonAccess(true)
        assertEquals(SongCardClips(listOf(CardClip(100, 190)), false), songCardClips(lesson, listOf("b", "c"), access))
        assertEquals(SongCardClips(emptyList(), true), songCardClips(lesson, listOf("c"), access))
        val overlapping = lesson.deepCopy().apply { a("lyrics")[1].addProperty("endMs", 310) }
        assertEquals(SongCardClips(emptyList(), true), songCardClips(overlapping, listOf("b"), access))
        assertEquals(listOf(CardClip(100, 200)), groupCardClips(listOf(ClipUnit("x", 100, 200), ClipUnit("y", 201, 300)), listOf("x", "y"), 1))
    }

    @Test fun foreignLyricContributesNeitherWordsNorPractice() {
        val words = lesson.teachingVocabulary()
        assertEquals(listOf("es:shared", "es:standalone"), words.map { it.s("lexemeId") })
        assertEquals(listOf("a"), songWordLineIds(lesson, words.first()))
        assertTrue(lineLearning(lesson, lesson.a("lyrics")[2], LessonAccess(false)).words.isEmpty())
        assertTrue(lesson.a("questions").all(::usablePracticeQuestion))
        assertEquals(listOf("native-q"), preparePracticeQuestions(lesson).map { it.key.questionId })
        val listening = prepareListeningQuestions(lesson)
        assertTrue(listening.isNotEmpty())
        assertTrue(listening.none { it.listening?.startMs == 200L || it.listening?.distractors?.values?.any { value -> value in setOf("good", "morning") } == true })
    }
}
