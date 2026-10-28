package com.example.medicat.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

data class Settings(
    val reminderDays: Int = DEFAULT_REMINDER_DAYS,
    val notificationsEnabled: Boolean = true,
    val reminderTime: LocalTime = DEFAULT_REMINDER_TIME,
    /** Look scanned barcodes up in public product databases. */
    val onlineLookup: Boolean = true
) {
    companion object {
        const val DEFAULT_REMINDER_DAYS = 30
        val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(9, 0)
        val REMINDER_OPTIONS = listOf(7, 14, 30, 60, 90)
        val CUSTOM_DAYS_RANGE = 1..365
    }
}

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    val settings: Flow<Settings> = dataStore.data.map { prefs ->
        Settings(
            reminderDays = prefs[REMINDER_DAYS] ?: Settings.DEFAULT_REMINDER_DAYS,
            notificationsEnabled = prefs[NOTIFICATIONS_ENABLED] ?: true,
            reminderTime = LocalTime.of(
                prefs[REMINDER_HOUR] ?: Settings.DEFAULT_REMINDER_TIME.hour,
                prefs[REMINDER_MINUTE] ?: Settings.DEFAULT_REMINDER_TIME.minute
            ),
            onlineLookup = prefs[ONLINE_LOOKUP] ?: true
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setReminderDays(days: Int) {
        dataStore.edit { it[REMINDER_DAYS] = days.coerceIn(Settings.CUSTOM_DAYS_RANGE) }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setOnlineLookup(enabled: Boolean) {
        dataStore.edit { it[ONLINE_LOOKUP] = enabled }
    }

    suspend fun setReminderTime(time: LocalTime) {
        dataStore.edit {
            it[REMINDER_HOUR] = time.hour
            it[REMINDER_MINUTE] = time.minute
        }
    }

    private companion object {
        val REMINDER_DAYS = intPreferencesKey("reminder_days")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val ONLINE_LOOKUP = booleanPreferencesKey("online_lookup")
    }
}
