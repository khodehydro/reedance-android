package com.hydro.reedance

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.hydro.reedance.data.*
import com.hydro.reedance.install.*
import com.hydro.reedance.ui.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ReeTalkCardAudioTest {
    private val spec = ReeTalkPackSpec("en-t01-s01", "introductions", "en", "", 1, "", 1)
    private val localized = """{"fa":"سلام","en":"Hello"}"""
    private val example = """{"text":"Hello","translation":$localized}"""
    private val pronunciation = """{"ipa":"həˈləʊ","faReading":"هِلو"}"""
    private val refs = """["t7","t1","t0"]"""
    private fun document() = JsonParser.parseString("""{
        "schemaVersion":1,"contentType":"reetalk","dialogueId":"en-t01-s01","categoryId":"introductions",
        "targetLanguage":"en","locale":"en-US","level":"A1","contentVersionCode":1,
        "title":$localized,"objective":$localized,"media":{"audio":{"durationMs":800}},
        "speakers":[{"id":"a","name":"Sara","gender":"woman"},{"id":"b","name":"Amir","gender":"man"}],
        "learning":{
          "words":[{"id":"word","text":"Hello","meaning":$localized,"pronunciation":$pronunciation,"example":$example,"evidenceTurnIds":$refs}],
          "grammar":[{"id":"grammar","title":$localized,"explanation":$localized,"takeaway":$localized,
            "example":$example,"tables":[{"title":$localized,"columns":[$localized],"rows":[[$localized]]}],"evidenceTurnIds":$refs}],
          "phrases":[{"id":"phrase","text":"Hello","meaning":$localized,"context":$localized,"pronunciation":$pronunciation,"example":$example,"evidenceTurnIds":$refs}],
          "questions":[{"id":"question","type":"multiple_choice","stage":"gist","prompt":$localized,"explanation":$localized,
            "options":[{"id":"yes","text":$localized},{"id":"no","text":$localized}],"correctOptionId":"yes","evidenceTurnIds":$refs}]
        }
    }""").asJsonObject.apply {
        add("turns", JsonArray().apply {
            repeat(8) { index -> add(JsonParser.parseString("""{
                "id":"t$index","speakerId":"${if(index % 2 == 0) "a" else "b"}","text":"Hello",
                "translation":$localized,"tokens":[{"surface":"Hello","gloss":$localized}],
                "startMs":${index * 100},"endMs":${index * 100 + 90}
            }""")) }
        })
    }

    @Test fun eightTurnPackValidatesAndCardsReadSplitEvidence() {
        val d = document()
        validateReeTalk(d, spec)
        val demo = InstalledReeTalk(d, File("audio.wav"), File("cover.png")).toReader()
        (demo.learning.words + demo.learning.grammar + demo.learning.phrases).forEach { card ->
            val ids = card.strings("evidenceTurnIds")
            assertEquals(listOf("t7", "t1", "t0"), ids)
            val clips = dialogueCardClips(demo.turns, ids, "card")
            assertEquals(listOf(0L to 190L, 700L to 790L), clips.map { it.startMs to it.endMs })
        }
        val question = dialogueEvidence(demo.turns, demo.learning.questions.single().evidenceTurnIds, 0)!!
        assertEquals(0L to 790L, question.startMs to question.endMs)
        d.a("turns").first().addProperty("speakerId", "b")
        assertThrows(IllegalArgumentException::class.java) { validateReeTalk(d, spec) }
    }

    @Test fun optionalEvidenceIsBackwardCompatibleAndRejectsInvalidReferences() {
        val d = document()
        val word = d.obj("learning").a("words").single()
        word.remove("evidenceTurnIds")
        validateReeTalk(d, spec)
        for(invalid in listOf("[]", "[\"unknown\"]", "[\"t0\",\"t0\"]", "[0]", "null")) {
            word.add("evidenceTurnIds", JsonParser.parseString(invalid))
            assertThrows(IllegalArgumentException::class.java) { validateReeTalk(d, spec) }
        }
    }
}
