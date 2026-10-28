package com.example.medicat.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ExpiryDatesTest {
    private val today = LocalDate.of(2026, 10, 27)

    private fun inDays(days: Long) = ExpiryDates.toMillis(today.plusDays(days))

    @Test
    fun `millis round trip keeps the date`() {
        assertEquals(today, ExpiryDates.toLocalDate(ExpiryDates.toMillis(today)))
    }

    @Test
    fun `status boundaries`() {
        assertEquals(ExpiryStatus.EXPIRED, ExpiryDates.statusOf(inDays(-1), 30, today))
        assertEquals(ExpiryStatus.EXPIRING_SOON, ExpiryDates.statusOf(inDays(0), 30, today))
        assertEquals(ExpiryStatus.EXPIRING_SOON, ExpiryDates.statusOf(inDays(30), 30, today))
        assertEquals(ExpiryStatus.SAFE, ExpiryDates.statusOf(inDays(31), 30, today))
    }

    @Test
    fun `relative phrases`() {
        assertEquals("today", ExpiryDates.relative(0))
        assertEquals("in 5 days", ExpiryDates.relative(5))
        assertEquals("3 days ago", ExpiryDates.relative(-3))
    }
}
