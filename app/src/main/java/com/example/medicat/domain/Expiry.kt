package com.example.medicat.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

enum class ExpiryStatus { SAFE, EXPIRING_SOON, EXPIRED }

/**
 * Expiry dates are stored as epoch millis at UTC midnight, which is what the
 * Material date picker produces. All conversions go through here so the
 * timezone handling stays in one place.
 */
object ExpiryDates {
    private val displayFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

    fun toLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

    fun toMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun daysUntil(millis: Long, today: LocalDate = LocalDate.now()): Long =
        ChronoUnit.DAYS.between(today, toLocalDate(millis))

    fun statusOf(millis: Long, reminderDays: Int, today: LocalDate = LocalDate.now()): ExpiryStatus {
        val days = daysUntil(millis, today)
        return when {
            days < 0 -> ExpiryStatus.EXPIRED
            days <= reminderDays -> ExpiryStatus.EXPIRING_SOON
            else -> ExpiryStatus.SAFE
        }
    }

    fun format(millis: Long): String = toLocalDate(millis).format(displayFormat)

    /** Human phrase such as "in 12 days", "today" or "3 days ago". */
    fun relative(days: Long): String = when {
        days == 0L -> "today"
        days == 1L -> "tomorrow"
        days == -1L -> "yesterday"
        days > 0 -> "in $days days"
        else -> "${-days} days ago"
    }
}
