package com.hydro.reedance

import com.google.gson.JsonParser
import com.hydro.reedance.data.a
import com.hydro.reedance.data.s
import com.hydro.reedance.domain.gradeListening
import com.hydro.reedance.domain.prepareListeningQuestions
import org.junit.Assert.*
import org.junit.Test

class ListeningPracticeTest {
    private fun obj(json: String) = JsonParser.parseString(json).asJsonObject

    @Test fun usesPackagedChunksAndStopsAtTheNextSync() {
        val source = obj("""{
          "songId":"demo", "media":{"audio":{"path":"audio.wav","durationMs":9000}},
          "lyrics":[
            {"id":"first","text":"I keep hope alive.","translationFa":"امید را زنده نگه می‌دارم.","startMs":1000,"endMs":6000},
            {"id":"second","text":"Walk quietly home.","startMs":5000,"endMs":8000}
          ],
          "questions":[{"id":"authored","type":"order","lineId":"first","chunks":[{"text":"I keep"},{"text":"hope alive."}]}]
        }""")
        val original = source.deepCopy()
        val entries = prepareListeningQuestions(source)
        assertEquals(2, entries.size)
        val first = entries.first()
        val clip = first.listening!!
        assertEquals(1000L, clip.startMs)
        assertEquals(5000L, clip.endMs)
        assertEquals(8000L, entries.last().listening!!.endMs)
        assertEquals(listOf("I keep", "hope alive."), first.question.a("chunks").map { it.s("text") })
        assertTrue(clip.distractors.values.all { it in setOf("Walk", "quietly", "home.") })
        assertEquals(2, clip.distractors.size)
        assertTrue(gradeListening(first.question, listOf("part-0", "part-1")).correct)
        assertFalse(gradeListening(first.question, listOf("part-0", "extra-0")).correct)
        assertEquals(original, source)
    }

    @Test fun someClipsRequireBothOriginalRecognitionAndPersianOrdering() {
        val source = obj("""{
          "songId":"demo", "media":{"audio":{"path":"audio.wav","durationMs":9000}},
          "lyrics":[
            {"id":"first","text":"I keep hope alive.","translationFa":"امید را زنده نگه می‌دارم.","startMs":1000},
            {"id":"second","text":"Walk quietly home.","translationFa":"آرام به خانه برو.","startMs":4000},
            {"id":"third","text":"Keep moving.","translationFa":"به حرکت ادامه بده.","startMs":7000}
          ]
        }""")
        val entries = prepareListeningQuestions(source)
        assertEquals(listOf("listen:first", "listen-meaning:second", "listen:third"), entries.map { it.key.questionId })
        val entry = entries[1]
        val clip = entry.listening!!
        val meaning = clip.meaning!!
        assertEquals("Walk quietly home.", meaning.original)
        assertEquals(3, meaning.originalOptions.toSet().size)
        assertTrue(meaning.original in meaning.originalOptions)
        assertEquals(listOf("آرام", "به", "خانه", "برو."), entry.question.a("chunks").map { it.s("text") })
        assertTrue(clip.distractors.isNotEmpty())
        val answer = listOf("part-0", "part-1", "part-2", "part-3")
        assertTrue(gradeListening(entry.question, answer, meaning, meaning.original).correct)
        assertFalse(gradeListening(entry.question, answer, meaning, "Keep moving.").correct)
        assertFalse(gradeListening(entry.question, answer.reversed(), meaning, meaning.original).correct)
    }

    @Test fun identicalVisiblePiecesCanSwapButCannotBeReused() {
        val question = obj("""{"chunks":[{"id":"1","text":"Go"},{"id":"2","text":"Go"}]}""")
        assertTrue(gradeListening(question, listOf("2", "1")).correct)
        assertFalse(gradeListening(question, listOf("1", "1")).correct)
    }

    @Test fun skipsLinesWithoutAClipOrDistinctPackagedDistractors() {
        val source = obj("""{
          "media":{"audio":{"path":"audio.wav","durationMs":4000}},
          "lyrics":[{"id":"only","text":"Go go.","startMs":1},{"id":"invalid","text":"Go.","startMs":4000}]
        }""")
        assertTrue(prepareListeningQuestions(source).isEmpty())
    }
}
