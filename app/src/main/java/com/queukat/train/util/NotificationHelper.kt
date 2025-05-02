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
import androidx.core.net.toUri
import com.queukat.train.MainActivity
import com.queukat.train.R

object NotificationHelper {

    /* ---------- Reminder channel ---------- */
    private const val REMINDER_CHANNEL_ID = "TRAIN_REMINDER_CHANNEL"
    private const val REMINDER_CHANNEL_NAME = "Train Reminders"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                REMINDER_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Reminders for upcoming trains" }

            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showReminderNotification(
        context: Context,
        title: String,
        message: String,
        notificationId: Int = System.currentTimeMillis().toInt()
    ) {
        // Не показываем без разрешения
        if (!canPostNotifications(context)) return

        val manager = context.getSystemService(NotificationManager::class.java)

        val pendingIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = BitmapFactory.decodeResource(
            context.resources, R.mipmap.ic_my_new_icon
        )

        val builder = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_my_new_icon)
            .setContentTitle(title)
            .setContentText(message)
            .setLargeIcon(largeIcon)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        NotificationManagerCompat.from(context)
            .notify(notificationId, builder.build())
    }

    /* ---------- Update channel ---------- */
    private const val UPDATE_CHANNEL_ID = "UPDATE_CHANNEL"
    private const val UPDATE_NOTIFICATION_ID = 1003

    fun canPostNotifications(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showUpdateNotification(
        context: Context,
        latestVersion: String,
        releaseNotes: String?
    ) {
        // Не показываем без разрешения
        if (!canPostNotifications(context)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                UPDATE_CHANNEL_ID,
                "Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "New versions of TrainApp" }

            NotificationManagerCompat.from(context)
                .createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(Intent.ACTION_VIEW, "https://github.com/queukat/TrainApp/releases/latest".toUri()),
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Доступно обновление: v$latestVersion")
            .setContentText("Нажмите, чтобы скачать новую версию")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    releaseNotes ?: "Откройте страницу релиза, чтобы узнать подробности."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        NotificationManagerCompat.from(context)
            .notify(UPDATE_NOTIFICATION_ID, builder.build())
    }
}
