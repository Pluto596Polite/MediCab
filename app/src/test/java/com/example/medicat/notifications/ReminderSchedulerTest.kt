package com.example.medicat.notifications

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderSchedulerTest {
    private val at = { h: Int, m: Int -> LocalDateTime.of(2026, 10, 27, h, m) }

    @Test
    fun `later today when the time has not passed`() {
        assertEquals(Duration.ofMinutes(90), ReminderScheduler.delayUntil(LocalTime.of(9, 0), at(7, 30)))
    }

    @Test
    fun `tomorrow when the time has passed`() {
        assertEquals(Duration.ofHours(23), ReminderScheduler.delayUntil(LocalTime.of(9, 0), at(10, 0)))
    }

    @Test
    fun `a run landing just before its slot books tomorrow, not the same slot`() {
        val now = LocalDateTime.of(2026, 10, 27, 8, 59, 50)
        assertEquals(
            Duration.ofDays(1).plusSeconds(10),
            ReminderScheduler.delayUntil(LocalTime.of(9, 0), now)
        )
    }
}
