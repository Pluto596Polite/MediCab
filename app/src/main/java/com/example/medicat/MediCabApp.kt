package com.example.medicat

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.medicat.data.SettingsRepository
import com.example.medicat.notifications.ExpiryNotifier
import com.example.medicat.notifications.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class MediCabApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notifier: ExpiryNotifier
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var settingsRepository: SettingsRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notifier.createChannel()
        appScope.launch {
            scheduler.schedule(settingsRepository.current().reminderTime, replace = false)
        }
        // Photo-painted models were retired in favour of templates; remove their old pictures.
        appScope.launch(Dispatchers.IO) { File(filesDir, "models").deleteRecursively() }
    }
}
