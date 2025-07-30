package com.arkadst.dataaccessnotifier

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

object NotificationManager {

    fun requestNotificationPermission(
        context: Context,
        permissionLauncher: ActivityResultLauncher<String>
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )) {
                PackageManager.PERMISSION_GRANTED -> true
                else -> {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    false
                }
            }
        } else {
            // Permission not required for Android 12 and below
            true
        }
    }

    fun showLogoutNotification(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android 8.0+
        val channel = NotificationChannel(
            "logout_channel",
            "Logout Notifications",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for user logout events"
            setShowBadge(true)
            enableLights(true)
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)

        // Create intent to open the app
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification
        val notification = NotificationCompat.Builder(context, "logout_channel")
            .setContentTitle("You have been logged out")
            .setContentText("Please log in again to continue using the app.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        notificationManager.notify(1001, notification)
    }

    fun showAccessLogNotification(context: Context, logEntry: LogEntryProto) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android 8.0+
        val channel = NotificationChannel(
            "access_log_channel",
            "Access Log Notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for new data access events"
            setShowBadge(true)
            enableLights(true)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(channel)

        // Create intent to open the app
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use LogEntryManager for formatting
        val notificationId = logEntry.timestamp.hashCode()
        val displayTime = LogEntryManager.formatDisplayTime(logEntry.timestamp)

        // Build individual notification for this entry
        val notification = NotificationCompat.Builder(context, "access_log_channel")
            .setContentTitle("Data Access: ${logEntry.infoSystem}")
            .setContentText("$displayTime • ${logEntry.receiver}")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(LogEntryManager.formatExpandedNotification(logEntry))
            )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setDefaults(NotificationCompat.DEFAULT_SOUND)
            .setGroup("access_logs")
            .build()

        notificationManager.notify(notificationId, notification)
    }
}
