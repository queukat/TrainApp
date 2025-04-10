package com.queukat.train

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
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
import com.queukat.train.util.ReminderUtils
import com.queukat.train.util.UpdateCheck
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {

    //   SettingsActivity    onActivityResult
    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            //    -  –  
            recreate()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        actionBar?.hide()

        lifecycleScope.launch {

            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                val currentVer = packageInfo.versionName ?: "0.0.0"

                val result = UpdateCheck.checkForUpdates(currentVer)
                if (result.isUpdateAvailable) {
                    Toast.makeText(this@MainActivity, "New version: ${result.latestVersion}", Toast.LENGTH_LONG).show()
                }
            }
        }




        // 1) ё   ( Push)
        NotificationHelper.createNotificationChannel(this)

        // 2)   POST_NOTIFICATIONS (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        // 3)        (Android 12+)
        ReminderUtils.ensureExactAlarmPermission(this, 1002)

        // 4)  , ,   ё TrainViewModel
        val db = AppDatabase.getInstance(applicationContext)
        val repo = TrainRepository(db, applicationContext)
        val factory = TrainViewModelFactory(application, repo)
        val mainViewModel = ViewModelProvider(this, factory)[TrainViewModel::class.java]

        mainViewModel.loadStops()

        // 5)  Compose
        setContent {
            TrainAppTheme {
                MainScreen(
                    mainViewModel = mainViewModel,
                    //     SettingsActivity
                    onOpenSettings = {
                        val intent = Intent(this, SettingsActivity::class.java)
                        settingsLauncher.launch(intent)
                    }
                )
            }
        }
    }
}
