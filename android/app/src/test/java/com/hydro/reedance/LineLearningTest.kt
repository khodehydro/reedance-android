package com.hydro.reedance

import com.google.gson.JsonParser
import com.hydro.reedance.data.s
import com.hydro.reedance.domain.LessonAccess
import com.hydro.reedance.domain.lineLearning
import org.junit.Assert.assertEquals
import org.junit.Test

class LineLearningTest {
    @Test fun selectsOnlyContentLinkedToTheTappedLine() {
        val lesson = JsonParser.parseString("""{
            "locale":"en-US",
            "grammar":[{"id":"match","example":{"text":"COME home!"}},{"id":"other","example":{"text":"Stay home"}}],
            "idioms":[{"id":"linked","lineId":"l2","text":"Come back"},{"id":"wrong-ref","lineId":"l1","text":"Come home"}],
            "sentences":[{"id":"same-text","text":"Come home."}],
            "vocabulary":[{"lexemeId":"en:home","lemma":"home"},{"lexemeId":"en:stay","lemma":"stay"}]
        }""").asJsonObject
        val line = JsonParser.parseString("""{
            "id":"l2","text":"Come home", "tokens":[{"surface":"Come"},{"surface":"home","lexemeId":"en:home"}]
        }""").asJsonObject
        val result = lineLearning(lesson, line, LessonAccess(premium = false))
        assertEquals(listOf("match"), result.grammar.map { it.s("id") })
        assertEquals(listOf("linked", "same-text"), result.phrases.map { it.second.s("id") })
        assertEquals(listOf("Come", "home"), result.words.map { it.s("lemma") })
    }
}
