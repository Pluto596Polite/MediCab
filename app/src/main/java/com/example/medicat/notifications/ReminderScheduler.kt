package com.example.medicat.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Books the daily expiry check for the user's chosen time of day. */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager get() = WorkManager.getInstance(context)

    /**
     * Ensures a run is booked for [time]. With [replace] an existing booking is
     * moved (used when the user changes the time); otherwise it is kept.
     */
    fun schedule(time: LocalTime, replace: Boolean) {
        workManager.cancelUniqueWork(LEGACY_PERIODIC_WORK)
        workManager.enqueueUniqueWork(
            UNIQUE_WORK,
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request(time)
        )
    }

    /** Called from inside a scheduled run to book tomorrow's, after the current one finishes. */
    fun scheduleNext(time: LocalTime) {
        workManager.enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request(time))
    }

    fun runNow() {
        workManager.enqueue(OneTimeWorkRequestBuilder<ExpiryCheckWorker>().build())
    }

    private fun request(time: LocalTime): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<ExpiryCheckWorker>()
            .setInitialDelay(delayUntil(time).toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_SCHEDULED to true))
            .build()

    companion object {
        const val KEY_SCHEDULED = "scheduled"
        private const val UNIQUE_WORK = "expiry_reminder"
        private const val LEGACY_PERIODIC_WORK = "daily_expiry_check"

        /**
         * Time until the next occurrence of [time]. A run that lands a few seconds
         * early must not book the same slot again, hence the one-minute margin.
         */
        fun delayUntil(time: LocalTime, now: LocalDateTime = LocalDateTime.now()): Duration {
            var next = now.toLocalDate().atTime(time)
            if (next.isBefore(now.plusMinutes(1))) next = next.plusDays(1)
            return Duration.between(now, next)
        }
    }
}
