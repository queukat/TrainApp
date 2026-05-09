package com.queukat.train.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class UpdateResult(
    val isUpdateAvailable: Boolean,
    val latestVersion: String,
    val releaseNotes: String? = null,
    val error: String? = null,
)

object UpdateCheck {
    private const val TAG = "UpdateCheck"

    private const val PREFS = "train_prefs"
    private const val KEY_LAST_CHECK_MS = "update_last_check_ms"
    private const val KEY_CACHED_TAG = "update_cached_tag"
    private const val KEY_CACHED_NOTES = "update_cached_notes"
    private const val CHECK_TTL_MS = 6L * 60 * 60 * 1000 // 6 hours

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/queukat/TrainApp/releases/latest"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    suspend fun checkForUpdates(context: Context): UpdateResult {
        val currentVersion = context.safeVersionName()

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_MS, 0L)
        if (now - lastCheck < CHECK_TTL_MS) {
            val cachedTag = prefs.getString(KEY_CACHED_TAG, null)
            if (!cachedTag.isNullOrBlank()) {
                val cachedNotes = prefs.getString(KEY_CACHED_NOTES, null)
                return UpdateResult(
                    isUpdateAvailable = isRemoteNewer(currentVersion, cachedTag),
                    latestVersion = cachedTag,
                    releaseNotes = cachedNotes,
                )
            }
        }

        return try {
            val latestJson = httpGet(LATEST_RELEASE_URL)
            val root = JSONObject(latestJson)

            val latestTag = root.optString("tag_name", "0.0.0")
            val releaseNotes = root.optString("body").takeIf { it.isNotBlank() }

            prefs.edit {
                putLong(KEY_LAST_CHECK_MS, now)
                putString(KEY_CACHED_TAG, latestTag)
                putString(KEY_CACHED_NOTES, releaseNotes)
            }

            UpdateResult(isRemoteNewer(currentVersion, latestTag), latestTag, releaseNotes)
        } catch (ex: Exception) {
            Log.e(TAG, "Update check failed", ex)
            UpdateResult(false, currentVersion, null, ex.message)
        }
    }

    private fun Context.safeVersionName(): String =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager
                    .getPackageInfo(
                        packageName,
                        PackageManager.PackageInfoFlags.of(0),
                    ).versionName ?: "0.0.0"
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.0"
            }
        } catch (_: Exception) {
            "0.0.0"
        }

    private fun parseSemVer(v: String): Triple<Int, Int, Int> {
        val parts =
            v
                .trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore('-')
                .split('.')
        return Triple(
            parts.getOrNull(0)?.toIntOrNull() ?: 0,
            parts.getOrNull(1)?.toIntOrNull() ?: 0,
            parts.getOrNull(2)?.toIntOrNull() ?: 0,
        )
    }

    private fun isRemoteNewer(
        local: String,
        remote: String,
    ): Boolean {
        val (lMaj, lMin, lPat) = parseSemVer(local)
        val (rMaj, rMin, rPat) = parseSemVer(remote)
        return when {
            rMaj != lMaj -> rMaj > lMaj
            rMin != lMin -> rMin > lMin
            else -> rPat > lPat
        }
    }

    @Throws(IOException::class)
    private suspend fun httpGet(url: String): String =
        withContext(Dispatchers.IO) {
            val req =
                Request
                    .Builder()
                    .url(url)
                    .header(
                        "User-Agent",
                        "TrainApp-UpdateChecker/1.0 (+https://github.com/queukat/TrainApp)",
                    ).header("Accept", "application/vnd.github+json")
                    .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                resp.body?.string() ?: throw IOException("Empty body")
            }
        }
}
