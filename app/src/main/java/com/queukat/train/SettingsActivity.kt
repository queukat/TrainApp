package com.queukat.train

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.edit
import com.queukat.train.ui.SettingsScreen
import com.queukat.train.ui.SettingsScreenActions
import com.queukat.train.ui.SettingsScreenState
import com.queukat.train.ui.theme.TrainAppTheme
import com.queukat.train.util.ReminderReceiver
import com.queukat.train.util.applyForcedAppLocale

private const val TAG = "SettingsActivity"

/**
 *    ( ,    ..).
 */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val forcedUiLocale = intent?.getStringExtra(MainActivity.EXTRA_FORCE_UI_LOCALE).orEmpty()
        applyForcedAppLocale(this, forcedUiLocale)

        val prefs = getSharedPreferences("train_prefs", MODE_PRIVATE)

        //   (  3 )
        val languages = listOf("en", "me", "meCyr")
        val currentLang =
            prefs
                .getString("appLanguage", "en")
                ?.takeIf { it in languages }
                ?: "en"

        // Stored defaults should represent real reminder actions used by the dialog.
        val reminderOptions = listOf("push", "calendar", "both", "none")
        val currentRem =
            prefs
                .getString("defaultReminderAction", "push")
                ?.takeIf { it in reminderOptions }
                ?: "push"

        //
        val defMins = prefs.getInt("defaultMinutesBefore", 15)
        val autoRefresh = prefs.getBoolean("autoRefreshTime", true)

        setContent {
            TrainAppTheme {
                //   ё Surface,    colorScheme.background
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    SettingsScreen(
                        state =
                            SettingsScreenState(
                                languages = languages,
                                reminderOptions = reminderOptions,
                                initialLanguage = currentLang,
                                initialReminder = currentRem,
                                initialMinutes = defMins,
                                initialAutoRefresh = autoRefresh,
                            ),
                        actions =
                            SettingsScreenActions(
                                onApply = { chosenLang, chosenReminder, minutes, autoRf ->
                                    //
                                    prefs.edit(commit = true) {
                                        putString("appLanguage", chosenLang)
                                        putString("defaultReminderAction", chosenReminder)
                                        putInt("defaultMinutesBefore", minutes)
                                        putBoolean("autoRefreshTime", autoRf)
                                    }
                                    Toast
                                        .makeText(
                                            this@SettingsActivity,
                                            getString(R.string.toast_settings_applied),
                                            Toast.LENGTH_SHORT,
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
                                },
                            ),
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
        if (!androidx.core.app.NotificationManagerCompat
                .from(this)
                .areNotificationsEnabled()
        ) {
            Toast
                .makeText(
                    this,
                    getString(R.string.toast_enable_notifications_settings),
                    Toast.LENGTH_LONG,
                ).show()

            startActivity(notificationSettingsIntent())
            return
        }

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 2) Android S+ exact alarms: вместо молча “return” лучше открыть системный экран разрешения
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Toast
                .makeText(
                    this,
                    getString(R.string.toast_enable_exact_alarms_settings),
                    Toast.LENGTH_LONG,
                ).show()
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            return
        }

        val intent =
            Intent(this, ReminderReceiver::class.java).apply {
                putExtra("trainNumber", "TEST")
                putExtra("minutesBefore", 0)
                putExtra("stationName", "PushTest")
            }

        val pendingIntent =
            PendingIntent.getBroadcast(
                this,
                "TEST_PUSH".hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val triggerTime = System.currentTimeMillis() + delayMs

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent,
            )

            val delaySeconds = (delayMs / 1000L).toInt()
            Toast
                .makeText(
                    this,
                    resources.getQuantityString(
                        R.plurals.toast_test_push_scheduled,
                        delaySeconds,
                        delaySeconds,
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot schedule exact alarm", e)
            Toast
                .makeText(
                    this,
                    getString(R.string.toast_cannot_schedule_exact_alarm, e.localizedMessage.orEmpty()),
                    Toast.LENGTH_LONG,
                ).show()
        }
    }

    private fun notificationSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        }
}
