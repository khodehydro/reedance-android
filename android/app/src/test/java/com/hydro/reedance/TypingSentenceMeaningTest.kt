package com.hydro.reedance

import com.google.gson.JsonParser
import com.hydro.reedance.ui.typingSentenceMeaning
import org.junit.Assert.assertEquals
import org.junit.Test

class TypingSentenceMeaningTest {
    private fun obj(json: String) = JsonParser.parseString(json).asJsonObject
    @Test fun usesExplicitFullTranslationInSelectedInterfaceLanguage() {
        val q = obj("""{"context":"I ____ music.","translationEn":"I love music.","translationFa":"من موسیقی را دوست دارم."}""")
        assertEquals("من موسیقی را دوست دارم.", typingSentenceMeaning(q, obj("{}"), true))
        assertEquals("I love music.", typingSentenceMeaning(q, obj("{}"), false))
    }
    @Test fun resolvesExistingFullSentenceTranslationFromLesson() {
        val q = obj("""{"context":"I ____ music.","accepted":["love"]}""")
        val lesson = obj("""{"vocabulary":[{"example":{"text":"I love music.","translationFa":"من موسیقی را دوست دارم."}}]}""")
        assertEquals("من موسیقی را دوست دارم.", typingSentenceMeaning(q, lesson, true))
    }
    @Test fun missingTranslationDoesNotInventHintOrUseExplanation() {
        val q = obj("""{"context":"I ____ music.","accepted":["love"],"explanationFa":"Secret answer"}""")
        assertEquals("", typingSentenceMeaning(q, obj("{}"), true))
    }
}
