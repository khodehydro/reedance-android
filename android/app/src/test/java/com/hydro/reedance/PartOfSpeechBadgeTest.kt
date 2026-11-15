package com.hydro.reedance

import com.hydro.reedance.ui.WordClass
import com.hydro.reedance.ui.wordClass
import org.junit.Assert.assertEquals
import org.junit.Test

class PartOfSpeechBadgeTest {
    @Test fun persianLabelsMapToDistinctClasses() {
        assertEquals(WordClass.Noun, wordClass("اسم"))
        assertEquals(WordClass.Verb, wordClass("فعل"))
        assertEquals(WordClass.Adjective, wordClass("صفت"))
        assertEquals(WordClass.Adverb, wordClass("قيد"))
        assertEquals(WordClass.Preposition, wordClass("حرف اضافه"))
    }
    @Test fun exactMatchingDoesNotConfuseAdverbAndVerb() {
        assertEquals(WordClass.Adverb, wordClass(" ADV. "))
        assertEquals(WordClass.Verb, wordClass("verb"))
        assertEquals(WordClass.Pronoun, wordClass("pronoun"))
        assertEquals(WordClass.Adjective, wordClass("sıfat"))
        assertEquals(WordClass.Other, wordClass("اسم / صفت"))
        assertEquals(WordClass.Other, wordClass(""))
    }
}
