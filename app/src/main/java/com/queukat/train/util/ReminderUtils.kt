package com.queukat.train.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import androidx.core.net.toUri
import com.queukat.train.R

private const val MILLIS_PER_MINUTE = 60_000L
private const val MIN_TRIGGER_LEAD_TIME_MS = 2_000L
private const val MISSING_COORDINATES_LATITUDE = 42.0
private const val MISSING_COORDINATES_LONGITUDE = 19.0

sealed interface PushReminderScheduleResult {
    data object Scheduled : PushReminderScheduleResult

    data object NotificationPermissionMissing : PushReminderScheduleResult

    data object ExactAlarmPermissionMissing : PushReminderScheduleResult

    data object TriggerTimeTooSoon : PushReminderScheduleResult

    data class Failed(
        val reason: String? = null,
    ) : PushReminderScheduleResult
}

object ReminderUtils {
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
        stationName: String,
    ): PushReminderScheduleResult {
        if (!NotificationHelper.canPostNotifications(context)) {
            return PushReminderScheduleResult.NotificationPermissionMissing
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()
        ) {
            return PushReminderScheduleResult.ExactAlarmPermissionMissing
        }

        val triggerTime = departureTimeMs - minutesBefore * MILLIS_PER_MINUTE
        val now = System.currentTimeMillis()
        if (triggerTime <= now + MIN_TRIGGER_LEAD_TIME_MS) {
            return PushReminderScheduleResult.TriggerTimeTooSoon
        }

        val intent =
            Intent(context, ReminderReceiver::class.java).apply {
                putExtra("trainNumber", trainNumber)
                putExtra("minutesBefore", minutesBefore)
                putExtra("stationName", stationName)
            }

        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                (trainNumber + triggerTime).hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        return try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent,
            )
            PushReminderScheduleResult.Scheduled
        } catch (e: SecurityException) {
            PushReminderScheduleResult.Failed(e.localizedMessage)
        } catch (e: Exception) {
            PushReminderScheduleResult.Failed(e.localizedMessage)
        }
    }

    fun scheduleCalendarEvent(
        context: Context,
        title: String,
        description: String,
        beginTimeMs: Long,
        endTimeMs: Long,
        locationUri: String? = null,
    ): Boolean {
        val intent =
            Intent(Intent.ACTION_INSERT).apply {
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

        return if (intent.resolveActivity(context.packageManager) != null) {
            runCatching {
                context.startActivity(intent)
            }.isSuccess
        } else {
            false
        }
    }

    fun openLocationInMaps(
        context: Context,
        lat: Double,
        lng: Double,
        stationName: String,
    ) {
        if (lat == MISSING_COORDINATES_LATITUDE && lng == MISSING_COORDINATES_LONGITUDE) {
            Toast
                .makeText(
                    context,
                    context.getString(R.string.toast_no_coords, stationName),
                    Toast.LENGTH_SHORT,
                ).show()
            return
        }

        val encoded = Uri.encode(stationName)
        val uri = "geo:$lat,$lng?q=$lat,$lng($encoded)"
        val mapIntent =
            Intent(Intent.ACTION_VIEW, uri.toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        if (mapIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapIntent)
        } else {
            Toast.makeText(context, R.string.toast_no_map_app, Toast.LENGTH_LONG).show()
        }
    }
}
