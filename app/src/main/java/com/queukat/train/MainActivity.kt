package com.queukat.train

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.queukat.train.data.db.AppDatabase
import com.queukat.train.data.repository.TrainRepository
import com.queukat.train.ui.MainScreen
import com.queukat.train.ui.TrainViewModel
import com.queukat.train.ui.TrainViewModelFactory
import com.queukat.train.ui.theme.TrainAppTheme
import com.queukat.train.util.NotificationHelper
import com.queukat.train.util.UpdateCheck
import com.queukat.train.util.UpdateResult
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { if (it.resultCode == RESULT_OK) recreate() }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 0) Notification channels
        NotificationHelper.createNotificationChannel(this)

        // 1) Update check (throttled inside UpdateCheck) + notify once per version.
        // Do not request notification permission here; that prompt belongs to the reminder flow.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val result = UpdateCheck.checkForUpdates(this@MainActivity)
                if (result.isUpdateAvailable && shouldNotifyUpdate(result.latestVersion)) {
                    if (NotificationHelper.canPostNotifications(this@MainActivity)) {
                        @Suppress("MissingPermission")
                        NotificationHelper.showUpdateNotification(
                            this@MainActivity,
                            result.latestVersion,
                            result.releaseNotes
                        )
                        markUpdateNotified(result.latestVersion)
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

        mainVM.loadStops()

        setContent {
            TrainAppTheme {
                MainScreen(
                    mainViewModel = mainVM,
                    onOpenSettings = {
                        val intent = Intent(this, SettingsActivity::class.java)
                        settingsLauncher.launch(intent)
                    }
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
        prefs.edit().putString("last_notified_update_version", latestVersion).apply()
    }
}
