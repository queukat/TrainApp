package com.queukat.train

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.queukat.train.data.db.AppDatabase
import com.queukat.train.data.db.StopEntity
import com.queukat.train.data.db.getNameForLanguage
import com.queukat.train.data.repository.TrainRepository
import com.queukat.train.ui.MainScreen
import com.queukat.train.ui.TrainViewModel
import com.queukat.train.ui.TrainViewModelFactory
import com.queukat.train.ui.findStopByAnyName
import com.queukat.train.ui.theme.TrainAppTheme
import com.queukat.train.util.DateTimeUtils
import com.queukat.train.util.NotificationHelper
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

        val launchArgs = readLaunchArgs()
        applyForcedAppLocale(this, launchArgs.forcedUiLocale)
        NotificationHelper.createNotificationChannel(this)

        val prefs = getSharedPreferences("train_prefs", MODE_PRIVATE)
        applyInitialLanguage(launchArgs.initialLanguage, prefs)

        val mainVM = createMainViewModel()
        applyInitialDate(mainVM, launchArgs.initialDate)
        mainVM.loadStops()
        launchInitialRouteRequestIfNeeded(mainVM, prefs, launchArgs)
        showMainScreen(mainVM)
    }

    private fun readLaunchArgs(): LaunchArgs =
        LaunchArgs(
            initialDate = intent?.getStringExtra(EXTRA_INITIAL_DATE).orEmpty(),
            initialFrom = intent?.getStringExtra(EXTRA_INITIAL_FROM).orEmpty(),
            initialTo = intent?.getStringExtra(EXTRA_INITIAL_TO).orEmpty(),
            initialLanguage = intent?.getStringExtra(EXTRA_INITIAL_LANGUAGE).orEmpty(),
            autoSearch = intent?.getBooleanExtra(EXTRA_AUTO_SEARCH, false) == true,
            forcedUiLocale = intent?.getStringExtra(EXTRA_FORCE_UI_LOCALE).orEmpty(),
        )

    private fun applyInitialLanguage(
        initialLanguage: String,
        prefs: android.content.SharedPreferences,
    ) {
        val normalizedLanguage = initialLanguage.takeIf { it in SUPPORTED_STATION_LANGUAGES }
        if (normalizedLanguage != null) {
            prefs.edit { putString("appLanguage", normalizedLanguage) }
            intent.removeExtra(EXTRA_INITIAL_LANGUAGE)
        }
    }

    private fun createMainViewModel(): TrainViewModel {
        val db = AppDatabase.getInstance(applicationContext)
        val repo = TrainRepository(db, applicationContext)
        val factory = TrainViewModelFactory(application, repo)
        return ViewModelProvider(this, factory)[TrainViewModel::class.java]
    }

    private fun applyInitialDate(
        mainVM: TrainViewModel,
        initialDate: String,
    ) {
        if (initialDate.isNotBlank()) {
            mainVM.setSelectedDate(initialDate)
        }
    }

    private fun launchInitialRouteRequestIfNeeded(
        mainVM: TrainViewModel,
        prefs: android.content.SharedPreferences,
        launchArgs: LaunchArgs,
    ) {
        if (!launchArgs.hasRouteRequest) return

        lifecycleScope.launch {
            val resolvedStops = mainVM.stops.filter { it.isNotEmpty() }.first()
            val stationLanguage = stationLanguage(prefs)
            val fromStop = applyInitialStation(resolvedStops, launchArgs.initialFrom, stationLanguage, mainVM::selectFromStop, mainVM::setFromStation)
            val toStop = applyInitialStation(resolvedStops, launchArgs.initialTo, stationLanguage, mainVM::selectToStop, mainVM::setToStation)
            maybeRunInitialSearch(mainVM, launchArgs, fromStop, toStop)
        }
    }

    private fun stationLanguage(prefs: android.content.SharedPreferences): String =
        prefs
            .getString("appLanguage", "en")
            ?.takeIf { it in SUPPORTED_STATION_LANGUAGES }
            ?: "en"

    private fun applyInitialStation(
        resolvedStops: List<StopEntity>,
        initialName: String,
        stationLanguage: String,
        selectStop: (StopEntity, String) -> Unit,
        setFallback: (String) -> Unit,
    ): StopEntity? {
        val stop = findStopByAnyName(resolvedStops, initialName)
        when {
            stop != null -> selectStop(stop, stop.getNameForLanguage(stationLanguage))
            initialName.isNotBlank() -> setFallback(initialName)
        }
        return stop
    }

    private fun maybeRunInitialSearch(
        mainVM: TrainViewModel,
        launchArgs: LaunchArgs,
        fromStop: StopEntity?,
        toStop: StopEntity?,
    ) {
        if (launchArgs.autoSearch && fromStop != null && toStop != null) {
            val searchDate = launchArgs.initialDate.ifBlank { DateTimeUtils.todayTrainDateString() }
            mainVM.setSelectedDate(searchDate)
            mainVM.loadRoutes(
                from = mainVM.fromStation.value,
                to = mainVM.toStation.value,
                date = searchDate,
            )
        }
    }

    private fun showMainScreen(mainVM: TrainViewModel) {
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

    companion object {
        private val SUPPORTED_STATION_LANGUAGES = setOf("en", "me", "meCyr")

        const val EXTRA_INITIAL_DATE = "com.queukat.train.extra.INITIAL_DATE"
        const val EXTRA_INITIAL_FROM = "com.queukat.train.extra.INITIAL_FROM"
        const val EXTRA_INITIAL_TO = "com.queukat.train.extra.INITIAL_TO"
        const val EXTRA_INITIAL_LANGUAGE = "com.queukat.train.extra.INITIAL_LANGUAGE"
        const val EXTRA_AUTO_SEARCH = "com.queukat.train.extra.AUTO_SEARCH"
        const val EXTRA_FORCE_UI_LOCALE = "com.queukat.train.extra.FORCE_UI_LOCALE"
    }
}

private data class LaunchArgs(
    val initialDate: String,
    val initialFrom: String,
    val initialTo: String,
    val initialLanguage: String,
    val autoSearch: Boolean,
    val forcedUiLocale: String,
) {
    val hasRouteRequest: Boolean
        get() = initialFrom.isNotBlank() || initialTo.isNotBlank() || autoSearch
}
