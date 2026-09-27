package com.securingtheinside.blocktext

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

object SmsNotifications {

    private const val CHANNEL_ID = "allowed_sms"

    fun show(context: Context, messageUri: Uri): Boolean {
        // Android 13 and later require notification permission.
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val manager = context.getSystemService(
            NotificationManager::class.java
        ) ?: return false

        // Creating an existing channel preserves the user's settings.
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Incoming messages",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for messages from allowed senders."
        }

        manager.createNotificationChannel(channel)

        if (!manager.areNotificationsEnabled()) {
            return false
        }

        if (
            manager.getNotificationChannel(CHANNEL_ID)?.importance ==
            NotificationManager.IMPORTANCE_NONE
        ) {
            return false
        }

        // A unique data URI gives each message its own pending intent.
        val openAppIntent = Intent(
            context,
            MainActivity::class.java
        ).apply {
            data = messageUri
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("New text message")
            .setContentText("Open BlockText to read it.")
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

        // Use the saved message URI as a unique notification tag.
        manager.notify(messageUri.toString(), 0, notification)

        return true
    }
}