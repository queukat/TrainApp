package com.queukat.train.util

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.Toast
import androidx.core.net.toUri
import com.queukat.train.R

object ReminderUtils {

    @Suppress("DEPRECATION")
    fun ensureExactAlarmPermission(activity: Activity, requestCode: Int = 1010) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val alarmManager = activity.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                activity.startActivityForResult(intent, requestCode)
            }
        }
    }

    /**
     * Push reminder через AlarmManager.
     * Теперь:
     * - учитывает Android 12+ exact alarm permission
     * - не ставит будильник в прошлое
     * - прокидывает stationName в уведомление
     */
    fun schedulePushNotification(
        context: Context,
        trainNumber: String,
        departureTimeMs: Long,
        minutesBefore: Int,
        stationName: String
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(
                    context,
                    "Exact alarms are not allowed. Please enable in system settings.",
                    Toast.LENGTH_LONG
                ).show()

                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(intent) }
                return
            }
        }

        val triggerTime = departureTimeMs - minutesBefore * 60_000L
        val now = System.currentTimeMillis()
        if (triggerTime <= now + 2_000L) {
            Toast.makeText(
                context,
                context.getString(R.string.toast_no_reminder_set),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("trainNumber", trainNumber)
            putExtra("minutesBefore", minutesBefore)
            putExtra("stationName", stationName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (trainNumber + triggerTime).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )

        Toast.makeText(
            context,
            context.getString(R.string.toast_reminder_set, trainNumber, minutesBefore),
            Toast.LENGTH_SHORT
        ).show()
    }

    fun scheduleCalendarEvent(
        context: Context,
        title: String,
        description: String,
        beginTimeMs: Long,
        endTimeMs: Long,
        locationUri: String? = null
    ) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginTimeMs)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTimeMs)
            if (!locationUri.isNullOrEmpty()) {
                putExtra(CalendarContract.Events.EVENT_LOCATION, locationUri)
            }
            putExtra(CalendarContract.Events.HAS_ALARM, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openLocationInMaps(context: Context, lat: Double, lng: Double, stationName: String) {
        if (lat == 42.0 && lng == 19.0) {
            Toast.makeText(
                context,
                context.getString(R.string.toast_no_coords, stationName),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val encoded = Uri.encode(stationName)
        val uri = "geo:$lat,$lng?q=$lat,$lng($encoded)"
        val mapIntent = Intent(Intent.ACTION_VIEW, uri.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (mapIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapIntent)
        } else {
            Toast.makeText(context, R.string.toast_no_map_app, Toast.LENGTH_LONG).show()
        }
    }
}
