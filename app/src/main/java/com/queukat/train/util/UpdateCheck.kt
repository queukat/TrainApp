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
        val currentVersion = context.safeVersionName()

        // 1) Ping (fire-and-forget)
        launch(Dispatchers.IO) { runCatching { sendPing(context, currentVersion) } }

        // 2) GitHub Latest Release
        try {
            val latestJson = httpGet(LATEST_RELEASE_URL)
            val root = JSONObject(latestJson)

            val latestTag    = root.optString("tag_name", "0.0.0")
            val releaseNotes = root.optString("body").takeIf { it.isNotBlank() }

            val updateAvailable = isRemoteNewer(currentVersion, latestTag)

            UpdateResult(updateAvailable, latestTag, releaseNotes)
        } catch (ex: Exception) {
            Log.e(TAG, "Update check failed", ex)
            UpdateResult(false, currentVersion, null, ex.message)
        }
    }

    // --- Internal helpers -------------------------------------------------

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
    } catch (_: Exception) { "0.0.0" }

    private fun parseSemVer(v: String): Triple<Int, Int, Int> {
        val p = v.trim().removePrefix("v").removePrefix("V").substringBefore('-').split('.')
        return Triple(p.getOrNull(0)?.toIntOrNull() ?: 0,
            p.getOrNull(1)?.toIntOrNull() ?: 0,
            p.getOrNull(2)?.toIntOrNull() ?: 0)
    }

    private fun isRemoteNewer(local: String, remote: String): Boolean {
        val (lMaj,lMin,lPat)=parseSemVer(local); val (rMaj,rMin,rPat)=parseSemVer(remote)
        return when { rMaj!=lMaj -> rMaj>lMaj; rMin!=lMin -> rMin>lMin; else -> rPat>lPat }
    }

    /** HEAD‑ping;  ,    UX. */
    private suspend fun sendPing(context: Context, currentVersion: String) =
        withContext(Dispatchers.IO) {
            val url = "$PING_URL?v=$currentVersion"
            val hash = hashedInstallId(context)
            val request = Request.Builder()
                .url(url)
                .get()
                .header("X-Install-Hash", hash)
                .build()

            httpClient.newCall(request).execute().use { resp ->
                Log.d(TAG, "Ping → $url   X-Install-Hash=$hash   ← HTTP ${'$'}{resp.code}")
            }
        }

    /**  GET  User‑Agent    . */
    @Throws(IOException::class)
    private suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent",
                "TrainApp-UpdateChecker/1.0 (+https://github.com/queukat/TrainApp)")
            .header("Accept", "application/vnd.github+json")
            .build()

        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${'$'}{resp.code}")
            resp.body?.string() ?: throw IOException("Empty body")
        }
    }
}
