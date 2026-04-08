package com.queukat.train

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsLanguageLabelsTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val prefs = context.getSharedPreferences("train_prefs", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        prefs.edit().putString("appLanguage", "meCyr").commit()
        device.wakeAndUnlock()
        device.pressHome()
    }

    @After
    fun tearDown() {
        prefs.edit().putString("appLanguage", "en").commit()
        device.pressHome()
    }

    @Test
    fun settings_showHumanReadableLanguageLabels_andPersistRawCodes() {
        launchSettings()

        assertNotNull(
            waitForObject(
                By.text(context.getString(R.string.station_language_montenegrin_cyrillic)),
                5_000
            )
        )

        val languageField = waitForEditTexts(count = 1, timeoutMs = 5_000).first()
        languageField.click()

        assertNotNull(waitForObject(By.text(context.getString(R.string.station_language_english)), 5_000))
        assertNotNull(
            waitForObject(
                By.text(context.getString(R.string.station_language_montenegrin_latin)),
                5_000
            )
        )
        assertNull(device.findObject(By.text("en")))
        assertNull(device.findObject(By.text("me")))
        assertNull(device.findObject(By.text("meCyr")))

        device.findObject(By.text(context.getString(R.string.station_language_english)))!!.click()
        device.findObject(By.text(context.getString(R.string.btn_apply)))!!.click()

        assertEquals("en", prefs.getString("appLanguage", null))

        launchSettings()

        assertNotNull(
            waitForObject(
                By.text(context.getString(R.string.station_language_english)),
                5_000
            )
        )
    }

    private fun launchSettings() {
        device.wakeAndUnlock()
        val intent = Intent(context, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        assertNotNull(
            waitForObject(
                By.text(context.getString(R.string.label_select_language_for_stations)),
                10_000
            )
        )
    }

    private fun waitForObject(selector: androidx.test.uiautomator.BySelector, timeoutMs: Long): UiObject2? {
        return device.wait(Until.findObject(selector), timeoutMs)
    }

    private fun waitForEditTexts(count: Int, timeoutMs: Long): List<UiObject2> {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val fields = device.findObjects(By.clazz("android.widget.EditText"))
            if (fields.size >= count) {
                return fields
            }
            Thread.sleep(200)
        }
        error("Expected at least $count EditText fields")
    }
}
