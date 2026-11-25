package com.hydro.reedance

import com.hydro.reedance.domain.alternateLyricLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricLanguageTest {
    @Test fun badgeOnlyAppearsForAnExplicitDifferentLanguage() {
        assertEquals("en", alternateLyricLanguage("es", "en"))
        assertEquals("tr", alternateLyricLanguage("es", "tr"))
        assertNull(alternateLyricLanguage("es", "es"))
        assertNull(alternateLyricLanguage("es", ""))
        assertNull(alternateLyricLanguage("es", "English"))
        assertNull(alternateLyricLanguage("", "en"))
    }
}
