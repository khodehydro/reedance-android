package com.hydro.reedance

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.hydro.reedance.data.*
import com.hydro.reedance.domain.*
import org.junit.Assert.*
import org.junit.Test

class ReviewQuestionsTest {
    private fun obj(text: String) = JsonParser.parseString(text).asJsonObject
    private fun word(lemma: String, sentence: String) = JsonObject().apply {
        addProperty("lexemeId", "w"); addProperty("lemma", lemma); addProperty("translationFa", "گرفتن")
        add("example", JsonObject().apply { addProperty("text", sentence); addProperty("translationFa", "معنی کامل جمله") })
    }
    @Test fun literalBoundaryRegexActuallyCreatesOneBlank() {
        val w = word("almak", "Bir kitap almak istiyorum.")
        val q = reviewQuestion(w, listOf(w), 1)!!
        assertEquals("Bir kitap ____ istiyorum.", q.s("context"))
        assertEquals(listOf("almak"), q.strings("accepted"))
        assertTrue(usablePracticeQuestion(q))
        assertTrue(grade(q, listOf("almak"), "tr-TR").correct)
    }
    @Test fun inflectedMissingRepeatedAndSubstringOnlyWordsUseHonestRecallPrompt() {
        for(sentence in listOf("Onu aldım.", "almak almak", "almaklar", "")) {
            val w = word("almak", sentence)
            val q = reviewQuestion(w, listOf(w), 1)!!
            assertTrue(q.s("context").isEmpty())
            assertEquals("واژهٔ مربوط به این معنی را بنویس.", q.s("promptFa"))
            assertEquals("گرفتن", q.s("translationFa"))
            assertTrue(usablePracticeQuestion(q))
        }
    }
    @Test fun noTranslationDoesNotCreateUnhelpfulCloze() {
        val w = word("almak", "almak")
        w.obj("example").remove("translationFa")
        assertEquals("", reviewQuestion(w, listOf(w), 1)!!.s("context"))
    }
    @Test fun choicesRequireDistinctMeaningfulDistractorsOtherwiseFallBack() {
        val w = word("almak", "almak")
        val sameMeaning = word("tutmak", "").apply { addProperty("lexemeId", "other") }
        assertEquals("typing", reviewQuestion(w, listOf(w, sameMeaning), 0)!!.s("type"))
        val other = word("ev", "").apply { addProperty("lexemeId", "house"); addProperty("translationFa", "خانه") }
        val q = reviewQuestion(w, listOf(w, other, other), 0)!!
        assertEquals("choice", q.s("type"))
        assertEquals(2, q.a("options").size)
        assertTrue(usablePracticeQuestion(q))
    }
    @Test fun incompleteWordIsNotConvertedToAnUnanswerableQuestion() {
        assertNull(reviewQuestion(obj("{}"), emptyList(), 0))
    }
    @Test fun invalidAuthoredQuestionsAreRejectedWithoutChangingTheirAnswers() {
        assertFalse(usablePracticeQuestion(obj("""{"id":"q","promptFa":"واژه جاافتاده","type":"typing","context":"A complete sentence.","accepted":["word"]}""")))
        assertFalse(usablePracticeQuestion(obj("""{"id":"q","promptFa":"سؤال","type":"choice","correctId":"a","options":[{"id":"a","fa":"قلب"},{"id":"b","fa":"قلب"}]}""")))
        assertFalse(usablePracticeQuestion(obj("""{"id":"q","promptFa":"سؤال","type":"match","pairs":[{"id":"a","text":"one","fa":"یک"},{"id":"a","text":"two","fa":"دو"}]}""")))
        assertFalse(usablePracticeQuestion(obj("""{"id":"q","promptFa":"سؤال","type":"order","chunks":[{"id":"a","text":"one"},{"id":"b","text":"two"}],"presentationOrder":["a","missing"]}""")))
    }
}
