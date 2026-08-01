package com.queukat.train.screenshots

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.LocaleList
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.queukat.train.MainActivity
import com.queukat.train.SettingsActivity
import com.queukat.train.data.api.RetrofitClient
import com.queukat.train.liveApiReachable
import com.queukat.train.wakeAndUnlock
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
class StoreScreenshotTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val prefs = context.getSharedPreferences("train_prefs", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        device.wakeAndUnlock()
        runCatching { device.setOrientationNatural() }
        device.pressHome()
    }

    @After
    fun tearDown() {
        runCatching { device.unfreezeRotation() }
        device.pressHome()
    }

    @Test
    fun captureStoreScreenshots() {
        assumeTrue("Live API is not reachable from this runtime", liveApiReachable())

        val args = InstrumentationRegistry.getArguments()
        val localeTag = args.getString("localeTag") ?: "en-US"
        val stationLanguage = args.getString("stationLanguage") ?: defaultStationLanguage(localeTag)
        val routeLabels = routeLabelsFor(stationLanguage)

        resetAppState(stationLanguage)
        applyAppLocale(localeTag)

        captureHome(localeTag)

        val searchDate =
            findNextDateWithResults(
                start = "Podgorica",
                finish = "Bar",
            )

        captureSearchResults(
            localeTag = localeTag,
            searchDate = searchDate,
            fromInput = "Podgorica",
            toInput = "Bar",
            routeLabelFragment = "${routeLabels.first} - ${routeLabels.second}",
        )

        captureExpandedCard(
            localeTag = localeTag,
            searchDate = searchDate,
            fromInput = "Podgorica",
            toInput = "Bar",
            routeLabelFragment = "${routeLabels.first} - ${routeLabels.second}",
        )

        captureSettings(localeTag)
    }

    private fun captureHome(localeTag: String) {
        launchMainActivity()
        waitForEditTexts(count = 2, timeoutMs = 20_000)
        captureScreenshot(localeTag, "01-home.png")
    }

    private fun captureSearchResults(
        localeTag: String,
        searchDate: String,
        fromInput: String,
        toInput: String,
        routeLabelFragment: String,
    ) {
        launchMainActivity(
            initialDate = searchDate,
            initialFrom = fromInput,
            initialTo = toInput,
            autoSearch = true,
        )
        assertNotNull(
            "Expected route card for '$routeLabelFragment'",
            waitForObject(By.textContains(routeLabelFragment), 20_000),
        )
        captureScreenshot(localeTag, "02-search-results.png")
    }

    private fun captureExpandedCard(
        localeTag: String,
        searchDate: String,
        fromInput: String,
        toInput: String,
        routeLabelFragment: String,
    ) {
        launchMainActivity(
            initialDate = searchDate,
            initialFrom = fromInput,
            initialTo = toInput,
            autoSearch = true,
        )

        val cardTitle = waitForObject(By.textContains(routeLabelFragment), 20_000)
        assertNotNull("Route card title not found for '$routeLabelFragment'", cardTitle)
        cardTitle!!.click()

        assertNotNull(
            "Expanded route content did not appear",
            waitForAnyObject(
                timeoutMs = 10_000,
                selectors = detailSelectorsFor(localeTag),
            ),
        )

        captureScreenshot(localeTag, "03-expanded-card.png")
    }

    private fun captureSettings(localeTag: String) {
        launchSettingsActivity()
        assertTrue(
            "Settings screen did not render",
            waitForEditTexts(count = 3, timeoutMs = 15_000).size >= 3,
        )
        captureScreenshot(localeTag, "04-settings.png")
    }

    private fun resetAppState(stationLanguage: String) {
        prefs
            .edit()
            .putString("appLanguage", stationLanguage)
            .putString("defaultReminderAction", "push")
            .putInt("defaultMinutesBefore", 15)
            .putBoolean("autoRefreshTime", true)
            .remove("saved_routes_v2")
            .remove("saved_routes")
            .remove("recent_searches_v1")
            .apply()
    }

    private fun applyAppLocale(localeTag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            localeManager?.applicationLocales = LocaleList.forLanguageTags(localeTag)
            SystemClock.sleep(500)
        }
    }

    private fun launchMainActivity(
        initialDate: String? = null,
        initialFrom: String? = null,
        initialTo: String? = null,
        autoSearch: Boolean = false,
    ) {
        device.wakeAndUnlock()
        device.pressHome()

        val intent =
            context.packageManager
                .getLaunchIntentForPackage(PACKAGE_NAME)
                ?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (!initialDate.isNullOrBlank()) {
                        putExtra(MainActivity.EXTRA_INITIAL_DATE, initialDate)
                    }
                    if (!initialFrom.isNullOrBlank()) {
                        putExtra(MainActivity.EXTRA_INITIAL_FROM, initialFrom)
                    }
                    if (!initialTo.isNullOrBlank()) {
                        putExtra(MainActivity.EXTRA_INITIAL_TO, initialTo)
                    }
                    putExtra(MainActivity.EXTRA_AUTO_SEARCH, autoSearch)
                }
                ?: error("Launch intent not found")

        context.startActivity(intent)
        waitForEditTexts(count = 2, timeoutMs = 20_000)
    }

    private fun launchSettingsActivity() {
        device.wakeAndUnlock()
        device.pressHome()

        val intent =
            Intent(context, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(intent)
    }

    private fun findNextDateWithResults(
        start: String,
        finish: String,
    ): String {
        val formatter =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Europe/Podgorica")
            }
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Podgorica"), Locale.US)

        repeat(7) {
            val candidate = formatter.format(calendar.time)
            val response = RetrofitClient.api.getRoutes(start, finish, candidate).execute()
            if (response.isSuccessful) {
                val body = response.body()
                val hasResults = !body?.direct.isNullOrEmpty() || !body?.connected.isNullOrEmpty()
                if (hasResults) {
                    return candidate
                }
            }
            calendar.add(Calendar.DATE, 1)
        }

        error("No live routes found for $start -> $finish in the next 7 days")
    }

    private fun captureScreenshot(
        localeTag: String,
        fileName: String,
    ) {
        SystemClock.sleep(800)
        device.waitForIdle()

        val outputDir =
            File(
                context.getExternalFilesDir(null),
                "store_screenshots/$localeTag",
            )
        outputDir.mkdirs()

        val outputFile = File(outputDir, fileName)
        val saved = device.takeScreenshot(outputFile)
        assertTrue("Failed to save screenshot to ${outputFile.absolutePath}", saved)
        assertTrue("Screenshot file was not created: ${outputFile.absolutePath}", outputFile.exists())
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

    private fun defaultStationLanguage(localeTag: String): String =
        when {
            localeTag.startsWith("ru", ignoreCase = true) -> "meCyr"
            localeTag.startsWith("cnr", ignoreCase = true) -> "me"
            localeTag.startsWith("sr", ignoreCase = true) -> "me"
            else -> "en"
        }

    private fun routeLabelsFor(stationLanguage: String): Pair<String, String> =
        when (stationLanguage) {
            "meCyr" -> "Подгорица" to "Бар"
            else -> "Podgorica" to "Bar"
        }

    private fun detailSelectorsFor(localeTag: String): List<BySelector> {
        val tokens =
            when {
                localeTag.startsWith("ru", ignoreCase = true) -> listOf("Приб.:", "отпр.:")
                localeTag.startsWith("cnr", ignoreCase = true) -> listOf("Dol.:", "pol.:", "Дол.:", "пол.:")
                localeTag.startsWith("sr", ignoreCase = true) -> listOf("Dol.:", "pol.:", "Дол.:", "пол.:")
                else -> listOf("Arr:", "dep:")
            }
        return tokens.map { By.textContains(it) }
    }

    private companion object {
        const val PACKAGE_NAME = "com.queukat.train"
    }
}
