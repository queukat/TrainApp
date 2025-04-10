package com.queukat.train.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import com.queukat.train.MainActivity
import com.queukat.train.R

object NotificationHelper {

    private const val CHANNEL_ID = "TRAIN_REMINDER_CHANNEL"
    private const val CHANNEL_NAME = "Train Reminders"
    //    ,   . .

    /**
     * ё   (      ,
     * ,  onCreate()  MainActivity).
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelDescription = "Reminders for upcoming trains"
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = channelDescription
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     *  :
     * - smallIcon = ic_my_new_icon
     * - contentIntent =  MainActivity  
     */
    fun showNotification(
        context: Context,
        title: String,
        message: String,
        notificationId: Int = System.currentTimeMillis().toInt()
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 1)    MainActivity  
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            // flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2) () large icon
        val largeIconBitmap = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_my_new_icon)

        // 3)  
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_my_new_icon) //  ,  ic_launcher_foreground  - ё
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .setLargeIcon(largeIconBitmap) //  ,       

        // 4) 
        manager.notify(notificationId, builder.build())
    }
}
