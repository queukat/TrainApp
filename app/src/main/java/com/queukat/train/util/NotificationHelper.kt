package com.queukat.train.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.queukat.train.MainActivity
import com.queukat.train.R

object NotificationHelper {
    private const val REMINDER_CHANNEL_ID = "TRAIN_REMINDER_CHANNEL"
    private const val LEGACY_UPDATE_CHANNEL_ID = "UPDATE_CHANNEL"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)

            val reminderName = context.getString(R.string.train_reminder_channel_name)
            val reminderDesc = context.getString(R.string.train_reminder_channel_description)
            val reminderChannel =
                NotificationChannel(
                    REMINDER_CHANNEL_ID,
                    reminderName,
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = reminderDesc }

            nm.createNotificationChannel(reminderChannel)
            nm.deleteNotificationChannel(LEGACY_UPDATE_CHANNEL_ID)
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showReminderNotification(
        context: Context,
        title: String,
        message: String,
        notificationId: Int = System.currentTimeMillis().toInt(),
    ) {
        if (!canPostNotifications(context)) return

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val largeIcon = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_my_new_icon)

        val builder =
            NotificationCompat
                .Builder(context, REMINDER_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_my_new_icon)
                .setContentTitle(title)
                .setContentText(message)
                .setLargeIcon(largeIcon)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    fun canPostNotifications(context: Context): Boolean {
        val appEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val permOk = hasNotificationRuntimePermission(context)

        return appEnabled && permOk
    }

    fun hasNotificationRuntimePermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

}
