package com.queukat.train

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.queukat.train.data.db.AppDatabase
import com.queukat.train.data.db.getNameForLanguage
import com.queukat.train.data.repository.TrainRepository
import com.queukat.train.ui.MainScreen
import com.queukat.train.ui.TrainViewModel
import com.queukat.train.ui.TrainViewModelFactory
import com.queukat.train.ui.findStopByAnyName
import com.queukat.train.ui.theme.TrainAppTheme
import com.queukat.train.util.DateTimeUtils
import com.queukat.train.util.NotificationHelper
import com.queukat.train.util.UpdateCheck
import com.queukat.train.util.applyForcedAppLocale
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val settingsLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { if (it.resultCode == RESULT_OK) recreate() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val skipUpdateCheck = intent?.getBooleanExtra(EXTRA_SKIP_UPDATE_CHECK, false) == true
        val initialDate = intent?.getStringExtra(EXTRA_INITIAL_DATE).orEmpty()
        val initialFrom = intent?.getStringExtra(EXTRA_INITIAL_FROM).orEmpty()
        val initialTo = intent?.getStringExtra(EXTRA_INITIAL_TO).orEmpty()
        val initialLanguage = intent?.getStringExtra(EXTRA_INITIAL_LANGUAGE).orEmpty()
        val autoSearch = intent?.getBooleanExtra(EXTRA_AUTO_SEARCH, false) == true
        val forcedUiLocale = intent?.getStringExtra(EXTRA_FORCE_UI_LOCALE).orEmpty()

        applyForcedAppLocale(this, forcedUiLocale)

        // 0) Notification channels
        NotificationHelper.createNotificationChannel(this)

        val prefs = getSharedPreferences("train_prefs", MODE_PRIVATE)
        val normalizedLanguage = initialLanguage.takeIf { it in setOf("en", "me", "meCyr") }
        if (normalizedLanguage != null) {
            prefs.edit { putString("appLanguage", normalizedLanguage) }
            intent.removeExtra(EXTRA_INITIAL_LANGUAGE)
        }

        // 1) Update check (throttled inside UpdateCheck) + notify once per version.
        // Do not request notification permission here; that prompt belongs to the reminder flow.
        if (!skipUpdateCheck) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    val result = UpdateCheck.checkForUpdates(this@MainActivity)
                    if (result.isUpdateAvailable && shouldNotifyUpdate(result.latestVersion)) {
                        if (NotificationHelper.canPostNotifications(this@MainActivity)) {
                            @Suppress("MissingPermission")
                            NotificationHelper.showUpdateNotification(
                                this@MainActivity,
                                result.latestVersion,
                                result.releaseNotes,
                            )
                            markUpdateNotified(result.latestVersion)
                        }
                    }
                }
            }
        }

        // 2) ViewModel + UI
        val db = AppDatabase.getInstance(applicationContext)
        val repo = TrainRepository(db, applicationContext)
        val factory = TrainViewModelFactory(application, repo)
        val mainVM: TrainViewModel =
            ViewModelProvider(this, factory)[TrainViewModel::class.java]

        if (initialDate.isNotBlank()) {
            mainVM.setSelectedDate(initialDate)
        }
        mainVM.loadStops()

        if (initialFrom.isNotBlank() || initialTo.isNotBlank() || autoSearch) {
            lifecycleScope.launch {
                val resolvedStops =
                    mainVM.stops
                        .filter { it.isNotEmpty() }
                        .first()
                val stationLanguage =
                    prefs
                        .getString("appLanguage", "en")
                        ?.takeIf { it in setOf("en", "me", "meCyr") }
                        ?: "en"

                val fromStop = findStopByAnyName(resolvedStops, initialFrom)
                if (fromStop != null) {
                    mainVM.selectFromStop(fromStop, fromStop.getNameForLanguage(stationLanguage))
                } else if (initialFrom.isNotBlank()) {
                    mainVM.setFromStation(initialFrom)
                }

                val toStop = findStopByAnyName(resolvedStops, initialTo)
                if (toStop != null) {
                    mainVM.selectToStop(toStop, toStop.getNameForLanguage(stationLanguage))
                } else if (initialTo.isNotBlank()) {
                    mainVM.setToStation(initialTo)
                }

                if (autoSearch && fromStop != null && toStop != null) {
                    val searchDate = initialDate.ifBlank { DateTimeUtils.todayTrainDateString() }
                    mainVM.setSelectedDate(searchDate)
                    mainVM.loadRoutes(
                        from = mainVM.fromStation.value,
                        to = mainVM.toStation.value,
                        date = searchDate,
                    )
                }
            }
        }

        setContent {
            TrainAppTheme {
                MainScreen(
                    mainViewModel = mainVM,
                    onOpenSettings = {
                        val intent = Intent(this, SettingsActivity::class.java)
                        settingsLauncher.launch(intent)
                    },
                )
            }
        }
    }

    private fun shouldNotifyUpdate(latestVersion: String): Boolean {
        val prefs = getSharedPreferences("train_prefs", MODE_PRIVATE)
        val lastNotified = prefs.getString("last_notified_update_version", null)
        return lastNotified != latestVersion
    }

    private fun markUpdateNotified(latestVersion: String) {
        val prefs = getSharedPreferences("train_prefs", MODE_PRIVATE)
        prefs.edit { putString("last_notified_update_version", latestVersion) }
    }

    companion object {
        const val EXTRA_SKIP_UPDATE_CHECK = "com.queukat.train.extra.SKIP_UPDATE_CHECK"
        const val EXTRA_INITIAL_DATE = "com.queukat.train.extra.INITIAL_DATE"
        const val EXTRA_INITIAL_FROM = "com.queukat.train.extra.INITIAL_FROM"
        const val EXTRA_INITIAL_TO = "com.queukat.train.extra.INITIAL_TO"
        const val EXTRA_INITIAL_LANGUAGE = "com.queukat.train.extra.INITIAL_LANGUAGE"
        const val EXTRA_AUTO_SEARCH = "com.queukat.train.extra.AUTO_SEARCH"
        const val EXTRA_FORCE_UI_LOCALE = "com.queukat.train.extra.FORCE_UI_LOCALE"
    }
}
