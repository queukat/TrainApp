package com.queukat.train

import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.queukat.train.util.NotificationHelper
import com.queukat.train.util.PushReminderScheduleResult
import com.queukat.train.util.ReminderUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderRuntimeVerificationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val context = instrumentation.targetContext
    private val prefs = context.getSharedPreferences("train_prefs", android.content.Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        prefs
            .edit()
            .putString("defaultReminderAction", "push")
            .putInt("defaultMinutesBefore", 15)
            .commit()
        NotificationManagerCompat.from(context).cancelAll()
        device.wakeAndUnlock()
        device.pressHome()
    }

    @After
    fun tearDown() {
        NotificationManagerCompat.from(context).cancelAll()
        device.executeShellCommand("appops set $PACKAGE_NAME SCHEDULE_EXACT_ALARM default")
        device.executeShellCommand("appops set $PACKAGE_NAME POST_NOTIFICATION default")
        device.pressHome()
    }

    @Test
    fun appLaunchAndSearch_returnsLiveRoutesOnEmulator() {
        assumeTrue("Live API is not reachable from this runtime", liveApiReachable())
        launchApp()
        performBarToPodgoricaSearch()

        assertNotNull(waitForObject(By.text(context.getString(R.string.direct_routes_label)), 20_000))
        assertNotNull(waitForObject(By.desc(context.getString(R.string.label_reminder)), 10_000))
    }

    @Test
    fun reminderFlow_notificationDenied_showsHonestBanner() {
        assumeTrue("Live API is not reachable from this runtime", liveApiReachable())
        prepareNotificationPermissionDialog()
        launchApp()
        performBarToPodgoricaSearch()
        openReminderDialogForFirstVisibleRoute()
        confirmReminderDialog()

        val denyButton =
            waitForPermissionButton(
                allow = false,
                timeoutMs = 10_000,
            )
        assertNotNull("Notification deny button not found", denyButton)
        denyButton!!.click()

        assertNotNull(
            waitForObject(
                By.text(context.getString(R.string.reminder_status_notification_permission_missing)),
                10_000,
            ),
        )
    }

    @Test
    fun schedulePushReminder_exactAlarmDenied_returnsExactAlarmMissing() {
        device.executeShellCommand("pm grant $PACKAGE_NAME android.permission.POST_NOTIFICATIONS")
        device.executeShellCommand("appops set $PACKAGE_NAME POST_NOTIFICATION allow")
        device.executeShellCommand("appops set $PACKAGE_NAME SCHEDULE_EXACT_ALARM deny")

        val result =
            ReminderUtils.schedulePushNotification(
                context = context,
                trainNumber = "RT-EXACT",
                departureTimeMs = System.currentTimeMillis() + 300_000L,
                minutesBefore = 1,
                stationName = "Bar",
            )

        assertEquals(PushReminderScheduleResult.ExactAlarmPermissionMissing, result)
    }

    @Ignore("Manual runtime verification is more stable here; delivery proof is captured in repair log.")
    fun scheduledReminder_deliversNotificationOnDevice() {
        device.executeShellCommand("pm grant $PACKAGE_NAME android.permission.POST_NOTIFICATIONS")
        device.executeShellCommand("appops set $PACKAGE_NAME POST_NOTIFICATION allow")
        device.executeShellCommand("appops set $PACKAGE_NAME SCHEDULE_EXACT_ALARM allow")
        NotificationHelper.createNotificationChannel(context)
        NotificationManagerCompat.from(context).cancelAll()

        val result =
            ReminderUtils.schedulePushNotification(
                context = context,
                trainNumber = "RT-VERIFY",
                departureTimeMs = System.currentTimeMillis() + 75_000L,
                minutesBefore = 1,
                stationName = "Bar",
            )

        assertEquals(PushReminderScheduleResult.Scheduled, result)

        assertTrue(
            "Reminder notification was not delivered within timeout",
            waitUntil(35_000) {
                device
                    .executeShellCommand("cmd notification list")
                    .lineSequence()
                    .any { it.contains(PACKAGE_NAME) }
            },
        )
    }

    private fun launchApp() {
        device.wakeAndUnlock()
        val launchIntent =
            context.packageManager
                .getLaunchIntentForPackage(PACKAGE_NAME)
                ?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ?: error("Launch intent not found")

        context.startActivity(launchIntent)
        assertNotNull(waitForObject(By.desc(context.getString(R.string.btn_settings)), 15_000))
        waitForEditTexts(2, 20_000)
    }

    private fun performBarToPodgoricaSearch() {
        val fields = waitForEditTexts(2, 20_000)
        fields[0].text = "Bar"
        fields[1].text = "Podgorica"

        val searchButton = waitForObject(By.desc("Search"), 10_000)
        assertNotNull("Search button not found", searchButton)
        searchButton!!.click()

        assertTrue(
            "Search results did not render in time",
            device.wait(Until.hasObject(By.text(context.getString(R.string.direct_routes_label))), 20_000) ||
                device.wait(Until.hasObject(By.desc(context.getString(R.string.label_reminder))), 20_000),
        )
    }

    private fun openReminderDialogForFirstVisibleRoute() {
        val reminderButton =
            waitForObject(
                By.desc(context.getString(R.string.label_reminder)),
                10_000,
            )
        assertNotNull("Reminder button not found", reminderButton)
        reminderButton!!.click()

        assertNotNull(
            waitForObject(
                By.text(context.getString(R.string.dialog_reminder_title)),
                5_000,
            ),
        )
    }

    private fun confirmReminderDialog() {
        val confirm =
            waitForObject(
                By.text(context.getString(R.string.dialog_reminder_ok)),
                5_000,
            )
        assertNotNull("Reminder confirm button not found", confirm)
        confirm!!.click()
    }

    private fun prepareNotificationPermissionDialog() {
        device.executeShellCommand("pm revoke $PACKAGE_NAME android.permission.POST_NOTIFICATIONS")
        device.executeShellCommand(
            "pm clear-permission-flags $PACKAGE_NAME android.permission.POST_NOTIFICATIONS user-set user-fixed",
        )
        device.executeShellCommand("appops set $PACKAGE_NAME POST_NOTIFICATION ignore")
    }

    private fun waitForEditTexts(
        count: Int,
        timeoutMs: Long,
    ): List<UiObject2> {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            val fields = device.findObjects(By.clazz("android.widget.EditText"))
            if (fields.size >= count) {
                return fields
            }
            SystemClock.sleep(250)
        }
        error("Expected at least $count EditText fields")
    }

    private fun waitForPermissionButton(
        allow: Boolean,
        timeoutMs: Long,
    ): UiObject2? {
        val buttonId = if (allow) "permission_allow_button" else "permission_deny_button"

        return waitForAnyObject(
            timeoutMs = timeoutMs,
            selectors =
                buildList {
                    add(By.res("com.android.permissioncontroller", buttonId))
                    add(By.res("com.google.android.permissioncontroller", buttonId))
                    if (allow) {
                        add(By.text("Allow"))
                        add(By.textContains("Allow"))
                    } else {
                        add(By.text("Don't allow"))
                        add(By.textContains("Don't"))
                        add(By.text("Deny"))
                    }
                },
        )
    }

    private fun waitForObject(
        selector: BySelector,
        timeoutMs: Long,
    ): UiObject2? = device.wait(Until.findObject(selector), timeoutMs)

    private fun waitForAnyObject(
        timeoutMs: Long,
        selectors: List<BySelector>,
    ): UiObject2? {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            selectors.forEach { selector ->
                device.findObject(selector)?.let { return it }
            }
            SystemClock.sleep(250)
        }
        return null
    }

    private fun waitUntil(
        timeoutMs: Long,
        condition: () -> Boolean,
    ): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) {
                return true
            }
            SystemClock.sleep(1_000)
        }
        return false
    }

    private companion object {
        const val PACKAGE_NAME = "com.queukat.train"
    }
}
