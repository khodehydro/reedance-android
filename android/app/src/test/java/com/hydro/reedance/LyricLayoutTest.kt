package com.hydro.reedance

import com.google.gson.JsonObject
import com.hydro.reedance.ui.timedLyricLine
import org.junit.Assert.*
import org.junit.Test

class LyricLayoutTest {
    private fun line(id: String, start: Long) = JsonObject().apply {
        addProperty("id", id); addProperty("startMs", start)
    }
    @Test fun followsClockBoundariesGapsAndBackwardSeeks() {
        val first = line("one", 1000); val second = line("two", 3000)
        val lines = listOf(first, second)
        assertSame(first, timedLyricLine(lines, 0))
        assertSame(first, timedLyricLine(lines, 2999))
        assertSame(second, timedLyricLine(lines, 3000))
        assertSame(second, timedLyricLine(lines, 9000))
        assertSame(first, timedLyricLine(lines, 1500))
        assertNull(timedLyricLine(emptyList(), 500))
        assertSame(first, timedLyricLine(lines.take(1), 9000))
    }
}
