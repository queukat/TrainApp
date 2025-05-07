package com.queukat.train

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.firebase.FirebaseApp
import com.queukat.train.data.db.AppDatabase
import com.queukat.train.data.repository.TrainRepository
import com.queukat.train.ui.MainScreen
import com.queukat.train.ui.TrainViewModel
import com.queukat.train.ui.TrainViewModelFactory
import com.queukat.train.ui.theme.TrainAppTheme
import com.queukat.train.util.NotificationHelper
import com.queukat.train.util.ReminderUtils
import com.queukat.train.util.UpdateCheck
import com.queukat.train.util.UpdateResult
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { if (it.resultCode == RESULT_OK) recreate() }

    private lateinit var notifPermissionLauncher: ActivityResultLauncher<String>
    private var pendingUpdate: UpdateResult? = null


    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)

        /* 0) канал REMINDER */
        NotificationHelper.createNotificationChannel(this)

        /* 1) launcher разрешения */
        notifPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) pendingUpdate?.let {
                @Suppress("MissingPermission")
                NotificationHelper.showUpdateNotification(
                    this, it.latestVersion, it.releaseNotes
                )
            }
            pendingUpdate = null
        }

        /* 2) проверяем обновление */
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val result = UpdateCheck.checkForUpdates(this@MainActivity)
                if (result.isUpdateAvailable) {
                    if (NotificationHelper.canPostNotifications(this@MainActivity)) {
                        @Suppress("MissingPermission")
                        NotificationHelper.showUpdateNotification(
                            this@MainActivity,
                            result.latestVersion,
                            result.releaseNotes
                        )
                    } else {
                        pendingUpdate = result
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Новая версия ${result.latestVersion} доступна!",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }

        /* 3) Exact Alarm permission */
        ReminderUtils.ensureExactAlarmPermission(this, 1002)

        /* 4) ViewModel + UI */
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
}
