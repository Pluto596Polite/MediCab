package com.example.medicat.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
    /** Look scanned barcodes and medication uses up in public databases. */
    val onlineLookup: Boolean = true,
    /** The user's name for greetings; blank until they set it. */
    val profileName: String = "",
    /** Look for a newer MediCab release on GitHub when the app opens. */
    val checkUpdates: Boolean = true,
    /** The day (ISO date) an update was last offered, so it's offered at most once a day. */
    val updateOfferedOn: String = ""
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
            onlineLookup = prefs[ONLINE_LOOKUP] ?: true,
            profileName = prefs[PROFILE_NAME].orEmpty(),
            checkUpdates = prefs[CHECK_UPDATES] ?: true,
            updateOfferedOn = prefs[UPDATE_OFFERED_ON].orEmpty()
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

    suspend fun setCheckUpdates(enabled: Boolean) {
        dataStore.edit { it[CHECK_UPDATES] = enabled }
    }

    suspend fun setUpdateOfferedOn(day: String) {
        dataStore.edit { it[UPDATE_OFFERED_ON] = day }
    }

    suspend fun setProfileName(name: String) {
        dataStore.edit { it[PROFILE_NAME] = name.trim().take(40) }
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
        val PROFILE_NAME = stringPreferencesKey("profile_name")
        val CHECK_UPDATES = booleanPreferencesKey("check_updates")
        val UPDATE_OFFERED_ON = stringPreferencesKey("update_offered_on")
    }
}
