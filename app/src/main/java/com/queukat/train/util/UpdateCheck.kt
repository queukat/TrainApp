package com.queukat.train.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.firebase.installations.FirebaseInstallations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * DTO   .
 */
data class UpdateResult(
    val isUpdateAvailable: Boolean,
    val latestVersion: String,
    val releaseNotes: String? = null,
    val error: String? = null
)

/**
 * Production-ready   +  ping  .
 *
 * •  `/releases/latest` GitHub-API   User-Agent.
 * •  **** HEAD-ping  Cloudflare Workers-endpoint  ё MAU.
 * •   `ANDROID_ID`   :  
 *   ,     .
 */
object UpdateCheck {

    // ---  --------------------------------------------------------

    private const val TAG = "UpdateCheck"

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/queukat/TrainApp/releases/latest"

    // Cloudflare Worker counting app installs.    GitHub-workflow, 
    //  .
    private const val PING_URL = "https://train-stats.queukat.workers.dev/ping"

    private const val SALT = "queukat-v1-hard-to-guess-string"

    // --- OkHttp -----------------------------------------------------------

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    // Memoized install hash —     
    @Volatile
    private var cachedInstallHash: String? = null

    private suspend fun hashedInstallId(context: Context): String =
        cachedInstallHash ?: withContext(Dispatchers.IO) {
            val rawId = obtainStableId(context)
            val md    = MessageDigest.getInstance("SHA-256")
            val hash  = md.digest((rawId + SALT).encodeToByteArray())
                .joinToString("") { "%02x".format(it) }
            cachedInstallHash = hash
            hash
        }

    /**
     *      .
     * 1) `ANDROID_ID` (  Android 8    signing key)
     * 2) Fallback → Firebase Installations ID (   reinstall)
     */
    private suspend fun obtainStableId(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        if (!androidId.isNullOrBlank()) return androidId
        // Fallback — Firebase ID ( ,  ,  )
        return FirebaseInstallations.getInstance().id.await()
    }

    // --- API --------------------------------------------------------------

    /**
     *       ping.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun checkForUpdates(context: Context): UpdateResult = coroutineScope {
        val currentVersion = context.versionName()

        // 1) Ping —   ,     
        launch(Dispatchers.IO) {
            runCatching { sendPing(context, currentVersion) }
        }

        // 2) GitHub Latest Release
        try {
            val latestJson = httpGet(LATEST_RELEASE_URL)
            val root = JSONObject(latestJson)

            val latestTag    = root.optString("tag_name", "0.0.0")
            val releaseNotes = root.optString("body").takeIf { it.isNotBlank() }

            val updateAvailable = isRemoteNewer(currentVersion, latestTag)

            UpdateResult(updateAvailable, latestTag, releaseNotes)
        } catch (ex: Exception) {
            Log.e(TAG, "Update check failed: ${'$'}{ex.message}")
            UpdateResult(
                isUpdateAvailable = false,
                latestVersion = currentVersion,
                releaseNotes = null,
                error = ex.message
            )
        }
    }

    // --- Internal helpers -------------------------------------------------

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun Context.versionName(): String =
        packageManager.getPackageInfo(
            packageName,
            PackageManager.PackageInfoFlags.of(0)
        ).versionName ?: "0.0.0"

    private fun parseSemVer(version: String): Triple<Int, Int, Int> {
        val cleaned = version
            .trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-') //  prerelease‑
        val parts = cleaned.split('.')
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return Triple(major, minor, patch)
    }

    private fun isRemoteNewer(local: String, remote: String): Boolean {
        val (lMaj, lMin, lPat) = parseSemVer(local)
        val (rMaj, rMin, rPat) = parseSemVer(remote)
        return when {
            rMaj != lMaj -> rMaj > lMaj
            rMin != lMin -> rMin > lMin
            else         -> rPat > lPat
        }
    }

    /** HEAD‑ping;  ,    UX. */
    private suspend fun sendPing(context: Context, currentVersion: String) =
        withContext(Dispatchers.IO) {
            val installHash = hashedInstallId(context)
            val request = Request.Builder()
                .url("${'$'}PING_URL?v=${'$'}currentVersion")
                .head()
                .header("X-Install-Hash", installHash)
                .build()
            httpClient.newCall(request).execute().close()
        }

    /**  GET  User‑Agent    . */
    @Throws(IOException::class)
    private fun httpGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "TrainApp-UpdateChecker/1.0 (+https://github.com/queukat/TrainApp)"
            )
            .build()

        httpClient.newCall(request).execute().use { response: Response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected code ${'$'}{response.code}")
            }
            return response.body?.string() ?: throw IOException("Empty body")
        }
    }
}
