package com.example.medicat.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.medicat.data.MedicationRepository
import com.example.medicat.data.SettingsRepository
import com.example.medicat.domain.ExpiryDates
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

/**
 * Notifies about active medication inside the reminder window. Scheduled runs
 * book the next day's run themselves, so the reminder fires at the exact time
 * the user picked instead of drifting like a periodic job.
 */
@HiltWorker
class ExpiryCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: MedicationRepository,
    private val settingsRepository: SettingsRepository,
    private val notifier: ExpiryNotifier,
    private val scheduler: ReminderScheduler
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = settingsRepository.current()
        if (inputData.getBoolean(ReminderScheduler.KEY_SCHEDULED, false)) {
            scheduler.scheduleNext(settings.reminderTime)
        }
        if (!settings.notificationsEnabled) return Result.success()

        val today = LocalDate.now()
        val cutoff = ExpiryDates.toMillis(today.plusDays(settings.reminderDays.toLong()))
        notifier.showExpiryAlert(repository.getActiveExpiringBefore(cutoff), today)
        return Result.success()
    }
}
