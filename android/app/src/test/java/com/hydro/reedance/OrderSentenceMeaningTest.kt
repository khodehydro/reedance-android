package com.hydro.reedance

import com.google.gson.JsonParser
import com.hydro.reedance.data.a
import com.hydro.reedance.domain.orderSentenceMeaningFa
import com.hydro.reedance.domain.preparePracticeQuestions
import org.junit.Assert.*
import org.junit.Test

class OrderSentenceMeaningTest {
    private fun obj(json: String) = JsonParser.parseString(json).asJsonObject

    @Test fun usesThePersianTranslationAuthoredInTheQuestion() {
        val question = obj("""{"type":"order","translationFa":"آمدم، چون دلم تنگ شده بود."}""")
        assertEquals("آمدم، چون دلم تنگ شده بود.", orderSentenceMeaningFa(question, obj("{}")))
    }

    @Test fun existingPackageUsesTheTranslationOfTheCompleteLineWithoutChangingTheQuestion() {
        val lesson = javaClass.getResourceAsStream("/lesson.json")!!.bufferedReader().use { obj(it.readText()) }
        val entry = preparePracticeQuestions(lesson).single { it.key.questionId == "line-order" }
        assertEquals("I keep", entry.question.a("chunks").first().get("text").asString)
        assertEquals("من امید را زنده نگه می‌دارم.", entry.orderMeaningFa)
        assertSame(lesson.a("questions").first { it.get("id").asString == "line-order" }, entry.question)
    }

    @Test fun aPartialLineAndAnExplanationDoNotCountAsFullTranslation() {
        val question = obj("""{"type":"order","lineId":"l1","explanationFa":"پاسخ درست", "chunks":[{"text":"I left"},{"text":"because it rained."}]}""")
        val lesson = obj("""{"lyrics":[{"id":"l1","text":"I left","translationFa":"رفتم"}]}""")
        assertEquals("", orderSentenceMeaningFa(question, lesson))
    }
}
