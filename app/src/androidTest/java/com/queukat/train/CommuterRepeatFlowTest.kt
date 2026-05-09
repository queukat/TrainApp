package com.queukat.train

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommuterRepeatFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val prefs = context.getSharedPreferences("train_prefs", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        prefs
            .edit()
            .putString("appLanguage", "en")
            .remove("saved_routes_v2")
            .remove("recent_searches_v1")
            .commit()
        device.wakeAndUnlock()
        device.pressHome()
    }

    @After
    fun tearDown() {
        prefs
            .edit()
            .putString("appLanguage", "en")
            .remove("saved_routes_v2")
            .remove("recent_searches_v1")
            .commit()
        device.pressHome()
    }

    @Test
    fun recentAndFavoriteRoutes_surviveRestart_andRunOneTapSearch() {
        assumeTrue("Live API is not reachable from this runtime", liveApiReachable())
        launchApp()

        performSearch("Bar", "Podgorica")
        clickText(context.getString(R.string.btn_save_route))
        assertNotNull(
            waitForObject(
                By.text(context.getString(R.string.label_recent_searches)),
                10_000,
            ),
        )

        performSearch("Podgorica", "Bar")
        assertNotNull(
            waitForObject(
                By.text("Podgorica - Bar"),
                10_000,
            ),
        )

        launchApp()

        assertNotNull(waitForObject(By.text(context.getString(R.string.label_favorite_routes)), 10_000))
        assertNotNull(waitForObject(By.text("Bar - Podgorica"), 10_000))
        assertNotNull(waitForObject(By.text(context.getString(R.string.label_recent_searches)), 10_000))
        assertNotNull(waitForObject(By.text("Podgorica - Bar"), 10_000))

        clickText("Bar - Podgorica")
        assertSearchResultsVisible()

        launchApp()

        clickText("Podgorica - Bar")
        assertSearchResultsVisible()
    }

    private fun launchApp() {
        device.wakeAndUnlock()
        val intent =
            context.packageManager
                .getLaunchIntentForPackage(PACKAGE_NAME)
                ?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ?: error("Launch intent not found")

        context.startActivity(intent)
        assertNotNull(waitForObject(By.desc(context.getString(R.string.btn_settings)), 15_000))
        waitForEditTexts(2, 20_000)
    }

    private fun performSearch(
        from: String,
        to: String,
    ) {
        val fields = waitForEditTexts(2, 20_000)
        fields[0].text = from
        fields[1].text = to

        val searchButton = waitForObject(By.desc("Search"), 10_000)
        assertNotNull("Search button not found", searchButton)
        searchButton!!.click()

        assertSearchResultsVisible()
    }

    private fun assertSearchResultsVisible() {
        assertNotNull(
            waitForAnyObject(
                timeoutMs = 20_000,
                selectors =
                    listOf(
                        By.text(context.getString(R.string.direct_routes_label)),
                        By.text(context.getString(R.string.connected_routes_label)),
                        By.desc(context.getString(R.string.label_reminder)),
                    ),
            ),
        )
    }

    private fun clickText(text: String) {
        val target = waitForObject(By.text(text), 10_000)
        assertNotNull("Expected UI text not found: $text", target)
        target!!.click()
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

    private companion object {
        const val PACKAGE_NAME = "com.queukat.train"
    }
}
