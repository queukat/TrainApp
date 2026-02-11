package com.queukat.train

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.edit
import com.queukat.train.ui.SettingsScreen
import com.queukat.train.ui.theme.TrainAppTheme
import com.queukat.train.util.ReminderReceiver

/**
 *    ( ,    ..).
 */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("train_prefs", MODE_PRIVATE)

        //   (  3 )
        val languages = listOf("en", "me", "meCyr")
        val currentLang = prefs.getString("appLanguage", "en") ?: "en"

        //  : push, calendar, ask, both, none
        val reminderOptions = listOf("push", "calendar", "ask", "both", "none")
        val currentRem = prefs.getString("defaultReminderAction", "ask") ?: "ask"

        //  
        val defMins = prefs.getInt("defaultMinutesBefore", 15)
        val autoRefresh = prefs.getBoolean("autoRefreshTime", true)

        setContent {
            TrainAppTheme {
                //   ё Surface,    colorScheme.background
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen(
                        languages = languages,
                        reminderOptions = reminderOptions,
                        initialLanguage = currentLang,
                        initialReminder = currentRem,
                        initialMinutes = defMins,
                        initialAutoRefresh = autoRefresh,
                        onApply = { chosenLang, chosenReminder, minutes, autoRf ->
                            //  
                            prefs.edit {
                                putString("appLanguage", chosenLang)
                                putString("defaultReminderAction", chosenReminder)
                                putInt("defaultMinutesBefore", minutes)
                                putBoolean("autoRefreshTime", autoRf)
                            }
                            Toast.makeText(
                                this@SettingsActivity,
                                getString(R.string.toast_settings_applied),
                                Toast.LENGTH_SHORT
                            ).show()
                            setResult(RESULT_OK)
                            finish()
                        },
                        onTestPushNow = {
                            scheduleTestPush(delayMs = 5000)
                        },
                        onTestPushLater = {
                            scheduleTestPush(delayMs = 60000)
                        },
                        onBackClick = {
                            //   " "  topbar
                            setResult(RESULT_CANCELED)
                            finish()
                        }
                    )
                }
            }
        }
    }

    /**
     *     AlarmManager.
     */
    private fun scheduleTestPush(delayMs: Long) {
        // 1) Если уведомления выключены (Android 12 и ниже — только так), ведём в настройки
        if (!androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            Toast.makeText(
                this,
                "Enable notifications for TrainMe in system settings",
                Toast.LENGTH_LONG
            ).show()

            val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
            }
            startActivity(intent)
            return
        }

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 2) Android S+ exact alarms: вместо молча “return” лучше открыть системный экран разрешения
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(
                    this,
                    "Exact alarms are not allowed. Please enable in system settings.",
                    Toast.LENGTH_LONG
                ).show()
                startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                return
            }
        }

        val intent = Intent(this, ReminderReceiver::class.java).apply {
            putExtra("trainNumber", "TEST")
            putExtra("minutesBefore", 0)
            putExtra("stationName", "PushTest")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            "TEST_PUSH".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerTime = System.currentTimeMillis() + delayMs

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }

            Toast.makeText(
                this,
                getString(R.string.toast_test_push_scheduled, delayMs / 1000),
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: SecurityException) {
            e.printStackTrace()
            Toast.makeText(
                this,
                "Cannot schedule exact alarm: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
