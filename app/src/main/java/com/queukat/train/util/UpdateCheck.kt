package com.queukat.train.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

data class UpdateResult(
    val isUpdateAvailable: Boolean,
    val latestVersion: String,
    val releaseNotes: String? = null,
    val error: String? = null
)

object UpdateCheck {

    private const val TAG = "UpdateCheck"

    private const val PREFS = "train_prefs"
    private const val KEY_LAST_CHECK_MS = "update_last_check_ms"
    private const val KEY_CACHED_TAG = "update_cached_tag"
    private const val KEY_CACHED_NOTES = "update_cached_notes"
    private const val CHECK_TTL_MS = 6L * 60 * 60 * 1000 // 6 часов

    private const val KEY_LAST_PING_DAY = "update_last_ping_day"

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/queukat/TrainApp/releases/latest"

    private const val PING_URL = "https://train-stats.queukat.workers.dev/ping"
    private const val SALT = "queukat-v1-hard-to-guess-string"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var cachedInstallHash: String? = null

    private suspend fun hashedInstallId(context: Context): String =
        cachedInstallHash ?: withContext(Dispatchers.IO) {
            val rawId = obtainStableId(context)
            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest((rawId + SALT).encodeToByteArray())
                .joinToString("") { "%02x".format(it) }
            cachedInstallHash = hash
            hash
        }

    private fun obtainStableId(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        if (!androidId.isNullOrBlank()) return androidId

        // крайний fallback: uuid в prefs (на практике почти не нужен)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString("install_uuid", null)
        if (!existing.isNullOrBlank()) return existing
        val newId = java.util.UUID.randomUUID().toString()
        prefs.edit().putString("install_uuid", newId).apply()
        return newId
    }

    suspend fun checkForUpdates(context: Context): UpdateResult = coroutineScope {
        val currentVersion = context.safeVersionName()

        // Ping не чаще 1 раза в сутки
        launch(Dispatchers.IO) { runCatching { sendPingThrottled(context, currentVersion) } }

        // Cached result (TTL)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_MS, 0L)
        if (now - lastCheck < CHECK_TTL_MS) {
            val cachedTag = prefs.getString(KEY_CACHED_TAG, null)
            if (!cachedTag.isNullOrBlank()) {
                val cachedNotes = prefs.getString(KEY_CACHED_NOTES, null)
                return@coroutineScope UpdateResult(
                    isUpdateAvailable = isRemoteNewer(currentVersion, cachedTag),
                    latestVersion = cachedTag,
                    releaseNotes = cachedNotes
                )
            }
        }

        // GitHub Latest Release
        try {
            val latestJson = httpGet(LATEST_RELEASE_URL)
            val root = JSONObject(latestJson)

            val latestTag = root.optString("tag_name", "0.0.0")
            val releaseNotes = root.optString("body").takeIf { it.isNotBlank() }

            prefs.edit()
                .putLong(KEY_LAST_CHECK_MS, now)
                .putString(KEY_CACHED_TAG, latestTag)
                .putString(KEY_CACHED_NOTES, releaseNotes)
                .apply()

            UpdateResult(isRemoteNewer(currentVersion, latestTag), latestTag, releaseNotes)
        } catch (ex: Exception) {
            Log.e(TAG, "Update check failed", ex)
            UpdateResult(false, currentVersion, null, ex.message)
        }
    }

    private fun Context.safeVersionName(): String = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(0)
            ).versionName ?: "0.0.0"
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.0"
        }
    } catch (_: Exception) {
        "0.0.0"
    }

    private fun parseSemVer(v: String): Triple<Int, Int, Int> {
        val p = v.trim().removePrefix("v").removePrefix("V").substringBefore('-').split('.')
        return Triple(
            p.getOrNull(0)?.toIntOrNull() ?: 0,
            p.getOrNull(1)?.toIntOrNull() ?: 0,
            p.getOrNull(2)?.toIntOrNull() ?: 0
        )
    }

    private fun isRemoteNewer(local: String, remote: String): Boolean {
        val (lMaj, lMin, lPat) = parseSemVer(local)
        val (rMaj, rMin, rPat) = parseSemVer(remote)
        return when {
            rMaj != lMaj -> rMaj > lMaj
            rMin != lMin -> rMin > lMin
            else -> rPat > lPat
        }
    }

    private suspend fun sendPingThrottled(context: Context, currentVersion: String) =
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

            val dayKey = SimpleDateFormat("yyyyMMdd", Locale.US).format(java.util.Date())
            val lastDay = prefs.getString(KEY_LAST_PING_DAY, null)
            if (lastDay == dayKey) return@withContext

            val url = "$PING_URL?v=$currentVersion"
            val hash = hashedInstallId(context)

            val request = Request.Builder()
                .url(url)
                .get()
                .header("X-Install-Hash", hash)
                .build()

            httpClient.newCall(request).execute().use { resp ->
                val shortHash = if (hash.length >= 8) hash.substring(0, 8) else hash
                Log.d(TAG, "Ping → HTTP ${resp.code} (hash=$shortHash...)")
            }

            prefs.edit().putString(KEY_LAST_PING_DAY, dayKey).apply()
        }

    @Throws(IOException::class)
    private suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "TrainApp-UpdateChecker/1.0 (+https://github.com/queukat/TrainApp)"
            )
            .header("Accept", "application/vnd.github+json")
            .build()

        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body?.string() ?: throw IOException("Empty body")
        }
    }
}
