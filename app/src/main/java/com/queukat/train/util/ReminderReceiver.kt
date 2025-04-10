package com.queukat.train.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.queukat.train.R

/**
 * ,    AlarmManager’   .
 *      NotificationHelper.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val trainNumber = intent.getStringExtra("trainNumber") ?: "Unknown Train"
        val minutesBefore = intent.getIntExtra("minutesBefore", 15)
        val stationName = intent.getStringExtra("stationName") ?: "Some station"

        // ё     strings.xml
        val title = context.getString(R.string.reminder_notification_title, trainNumber)
        val message = context.getString(R.string.reminder_notification_message, stationName, minutesBefore)

        NotificationHelper.showNotification(
            context = context,
            title = title,
            message = message
        )
    }
}
