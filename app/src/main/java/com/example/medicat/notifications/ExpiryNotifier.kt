package com.example.medicat.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.medicat.MainActivity
import com.example.medicat.R
import com.example.medicat.data.local.MedicationEntity
import com.example.medicat.domain.ExpiryDates
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpiryNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Expiry alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Reminders when medication is expired or about to expire"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPostNotifications(): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permissionGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Posts one summary notification for [medications], or clears it when the list is empty. */
    fun showExpiryAlert(medications: List<MedicationEntity>, today: LocalDate = LocalDate.now()) {
        val manager = NotificationManagerCompat.from(context)
        if (medications.isEmpty()) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (!canPostNotifications()) return

        val withDays = medications.map { it to ExpiryDates.daysUntil(it.expiryDate, today) }
        val expired = withDays.count { it.second < 0 }
        val expiring = withDays.size - expired

        val title = listOfNotNull(
            expired.takeIf { it > 0 }?.let { "$it expired" },
            expiring.takeIf { it > 0 }?.let { "$it expiring soon" }
        ).joinToString(" · ")

        val lines = withDays.map { (med, days) ->
            if (days < 0) "${med.name} — expired ${ExpiryDates.relative(days)}"
            else "${med.name} — expires ${ExpiryDates.relative(days)}"
        }

        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        lines.take(MAX_LINES).forEach(style::addLine)
        if (lines.size > MAX_LINES) style.setSummaryText("+${lines.size - MAX_LINES} more")

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(style)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post; nothing to do.
        }
    }

    private companion object {
        const val CHANNEL_ID = "expiry_alerts"
        const val NOTIFICATION_ID = 1001
        const val MAX_LINES = 5
    }
}
