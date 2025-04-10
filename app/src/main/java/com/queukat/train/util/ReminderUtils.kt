package com.queukat.train.util

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.Toast
import com.queukat.train.R
import androidx.core.net.toUri

/**
 *    (Push)     .
 */
object ReminderUtils {

    /**
     *        (Android 12+).
     */
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
     *   Push-  [AlarmManager].
     *
     * @param context — 
     * @param trainNumber —  () 
     * @param departureTimeMs —     
     * @param minutesBefore —    
     */
    fun schedulePushNotification(
        context: Context,
        trainNumber: String,
        departureTimeMs: Long,
        minutesBefore: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("trainNumber", trainNumber)
            putExtra("minutesBefore", minutesBefore)
            putExtra("stationName", "")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            trainNumber.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = departureTimeMs - minutesBefore * 60_000
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

    /**
     *      Intent ACTION_INSERT.
     * @param locationUri —       Location (: google maps link)
     */
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
        }
        //  Activity  ( )
        context.startActivity(intent)
    }

    /**
     *    .  lat/lng = fallback (42.0, 19.0),   « ».
     *   ,  geo: intent,    «   ».
     */
    fun openLocationInMaps(context: Context, lat: Double, lng: Double, stationName: String) {
        // 1)     ->    
        if (lat == 42.0 && lng == 19.0) {
            Toast.makeText(
                context,
                context.getString(R.string.toast_no_coords, stationName),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // 2)  geo:-Intent
        val uri = "geo:$lat,$lng?q=$lat,$lng($stationName)"
        val mapIntent = Intent(Intent.ACTION_VIEW, uri.toUri())
        //  ё  setPackage("com.google.android.apps.maps")
        //       -

        // 3) ,    ,   geo:
        if (mapIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapIntent)
        } else {
            Toast.makeText(
                context,
                R.string.toast_no_map_app,
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
