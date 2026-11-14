package com.hydro.reedance

import com.hydro.reedance.ui.currentPersianMonth
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class PersianMonthTest {
    private fun check(date: String, year: Int, month: Int, day: Int, count: Int) {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tehran"))
            val result = currentPersianMonth(Instant.parse(date).toEpochMilli())
            assertEquals(year, result.year)
            assertEquals(month, result.month)
            assertEquals(day, result.today)
            assertEquals(count, result.days.size)
            assertEquals(count, result.days.toSet().size)
            assertTrue(result.days.zipWithNext().all { (a, b) -> b - a == 1L })
        } finally { TimeZone.setDefault(previous) }
    }
    @Test fun farvardinHasThirtyOneDays() = check("2024-03-20T12:00:00Z", 1403, 0, 1, 31)
    @Test fun mehrHasThirtyDays() = check("2024-09-22T12:00:00Z", 1403, 6, 1, 30)
    @Test fun leapEsfandHasThirtyDays() = check("2025-03-20T12:00:00Z", 1403, 11, 30, 30)
    @Test fun ordinaryEsfandHasTwentyNineDays() = check("2023-03-20T12:00:00Z", 1401, 11, 29, 29)
}
