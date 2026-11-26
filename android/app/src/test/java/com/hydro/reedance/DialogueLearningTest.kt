package com.hydro.reedance

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.hydro.reedance.install.InstalledReeTalk
import com.hydro.reedance.ui.toReader
import java.io.File
import com.hydro.reedance.data.a
import com.hydro.reedance.data.obj
import com.hydro.reedance.data.s
import com.hydro.reedance.ui.dialogueCategories
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class DialogueLearningTest {
    @Test fun installedContentUsesPersianButKeepsOriginalDialogue() {
        val document = JsonParser.parseString("""{
            "speakers": [{"id":"a","name":"Sara"}],
            "turns": [{"id":"t1","speakerId":"a","text":"Hello",
                "translation":{"fa":"سلام","en":"Hello there"},
                "tokens":[{"gloss":{"fa":"درود","en":"Greeting"}}]}],
            "learning":{"questions":[{"prompt":{"fa":"چه گفت؟","en":"What did she say?"},
                "options":[{"id":"a","text":{"fa":"سلام","en":"Hello"}}],
                "correctOptionId":"a","explanation":{"fa":"سلام کرد","en":"She greeted him"}}]}
        }""").asJsonObject
        val demo = InstalledReeTalk(document, File("audio.wav"), File("cover.jpg")).toReader()
        val turn = demo.turns.single()
        assertEquals("Hello", turn.text)
        assertEquals("سلام", turn.meaning)
        assertEquals(listOf("درود"), turn.glosses)
        val question = demo.learning.questions.single()
        assertEquals("چه گفت؟", question.prompt)
        assertEquals(listOf("سلام"), question.options)
        assertEquals("سلام کرد", question.explanation)
    }

    private fun checkTable(table: JsonObject) {
        val columns = table.getAsJsonArray("columns")
        assertTrue(columns.size() > 0)
        val rows = table.getAsJsonArray("rows")
        assertTrue(rows.size() > 0)
        rows.forEach { row -> assertEquals(columns.size(), row.asJsonArray.size()) }
    }

    @Test fun eachDemoHasPronunciationsExamplesTablesAndExplainedQuestions() {
        dialogueCategories.flatMap { it.demos }.forEach { demo ->
            val learning = demo.learning
            checkTable(learning.wordTable)
            checkTable(learning.phraseTable)
            assertTrue(learning.words.isNotEmpty() && learning.phrases.isNotEmpty() && learning.grammar.isNotEmpty())
            (learning.words + learning.phrases).forEach { item ->
                assertTrue(item.s("faReading").isNotBlank())
                assertTrue(item.obj("example").s("text").isNotBlank())
            }
            learning.grammar.forEach { note ->
                assertTrue(note.s("bodyFa").isNotBlank() && note.obj("example").s("text").isNotBlank())
                assertTrue(note.a("tables").isNotEmpty())
                note.a("tables").forEach(::checkTable)
            }
            assertTrue(learning.questions.isNotEmpty())
            learning.questions.forEach { assertTrue(it.correct in it.options.indices && it.explanation.isNotBlank()) }
        }
    }
}
