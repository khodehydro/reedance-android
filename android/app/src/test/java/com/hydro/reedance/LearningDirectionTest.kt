package com.hydro.reedance

import com.hydro.reedance.ui.learningCellRtl
import org.junit.Assert.*
import org.junit.Test

class LearningDirectionTest {
    @Test fun persianAndMixedCellsAreRtlButOriginalWordsStayLtr() {
        assertTrue(learningCellRtl("kalp یعنی قلب (اسم)"))
        assertTrue(learningCellRtl("معنی فارسی"))
        assertFalse(learningCellRtl("gönül"))
        assertFalse(learningCellRtl("한국어"))
        assertFalse(learningCellRtl("123"))
        assertTrue(learningCellRtl("123", "rtl"))
        assertFalse(learningCellRtl("قلب", "ltr"))
    }
}
