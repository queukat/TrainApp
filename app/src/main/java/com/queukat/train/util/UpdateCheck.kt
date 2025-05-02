package com.queukat.train.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.firebase.installations.FirebaseInstallations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.tasks.await

/**
 * DTO результата проверки обновлений.
 */
data class UpdateResult(
    val isUpdateAvailable: Boolean,
    val latestVersion: String,
    val releaseNotes: String? = null,
    val error: String? = null
)

/**
 * Production-ready проверка обновлений + анонимный ping для статистики.
 *
 * • Запрашивает `/releases/latest` GitHub-API с корректным User-Agent.
 * • Отправляет HEAD-ping на Cloudflare Workers-endpoint для подсчёта MAU.
 * • Работает в IO-корутине, имеет таймауты и единый OkHttp-клиент.
 */
object UpdateCheck {

    // --- Константы --------------------------------------------------------

    private const val TAG = "UpdateCheck"

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/queukat/TrainApp/releases/latest"


    private const val PING_URL =
        "https://jellyfin-stats.queukat.workers.dev"

    private const val SALT = "queukat‑v1‑hard‑to‑guess‑string"

    // --- OkHttp -----------------------------------------------------------

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }


    private suspend fun hashedInstallId(): String = withContext(Dispatchers.IO) {
        // теперь await() возвращает String, а не Task<String>
        val rawId: String = FirebaseInstallations
            .getInstance()
            .id
            .await()

        val md   = MessageDigest.getInstance("SHA-256")
        val hash = md.digest((rawId + SALT).encodeToByteArray())
        hash.joinToString("") { "%02x".format(it) }
    }


    // --- API --------------------------------------------------------------

    /**
     * Проверяет наличие обновлений и отправляет статистический ping.
     *
     * @param context нужен только для получения текущей versionName
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun checkForUpdates(context: Context): UpdateResult =
        withContext(Dispatchers.IO) {
            val currentVersion = context.versionName()
            try {
                // 1) Ping — не блокирует основную логику
                sendPing(currentVersion)

                // 2) GitHub Latest Release
                val latestJson = httpGet(LATEST_RELEASE_URL)
                val root = JSONObject(latestJson)

                val latestTag = root.optString("tag_name", "0.0.0")
                val releaseNotes = root.optString("body").takeIf { it.isNotBlank() }

                val updateAvailable = isRemoteNewer(currentVersion, latestTag)

                UpdateResult(updateAvailable, latestTag, releaseNotes)
            } catch (ex: Exception) {
                Log.e(TAG, "Update check failed: ${ex.message}")
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
            .substringBefore('-') // обрезаем prerelease
        val parts = cleaned.split(".")
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

    /** HEAD-ping; ошибки глотаем, чтобы не ломать UX. */
    private suspend fun sendPing(currentVersion: String) {
        val installHash = hashedInstallId()                  // ← новый шаг
        val request = Request.Builder()
            .url("$PING_URL?v=$currentVersion")
            .head()
            .header("X-Install-Hash", installHash)           // <—
            .build()
        runCatching { httpClient.newCall(request).execute().close() }
    }

    /** Выполняет GET с User-Agent и возвращает тело ответа. */
    @Throws(IOException::class)
    private fun httpGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "TrainApp-UpdateChecker/1.0 (+https://github.com/queukat/TrainApp)")
            .build()
        httpClient.newCall(request).execute().use { response: Response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected code ${response.code}")
            }
            return response.body?.string() ?: throw IOException("Empty body")
        }
    }
}
