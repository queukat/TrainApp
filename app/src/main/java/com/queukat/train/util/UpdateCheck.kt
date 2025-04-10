// UpdateCheck.kt
package com.queukat.train.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

data class UpdateResult(
    val isUpdateAvailable: Boolean,
    val latestVersion: String,
    val releaseNotes: String? = null
)

private fun parseSemVer(version: String): Triple<Int, Int, Int> {
    val cleaned = version.trim().removePrefix("v").removePrefix("V")
    val parts = cleaned.split(".")
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.split("-")?.firstOrNull()?.toIntOrNull() ?: 0
    return Triple(major, minor, patch)
}

private fun isRemoteNewer(localVersion: String, remoteVersion: String): Boolean {
    val (lMaj, lMin, lPat) = parseSemVer(localVersion)
    val (rMaj, rMin, rPat) = parseSemVer(remoteVersion)
    return when {
        rMaj > lMaj -> true
        rMaj < lMaj -> false
        rMin > lMin -> true
        rMin < lMin -> false
        rPat > lPat -> true
        else -> false
    }
}

object UpdateCheck {
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/queukat/TrainApp/releases/latest"

    suspend fun checkForUpdates(currentVersionName: String): UpdateResult {
        return withContext(Dispatchers.IO) {
            try {
                val jsonString = URL(LATEST_RELEASE_URL).readText()
                val root = JSONObject(jsonString)

                val latestTag = root.optString("tag_name", "").ifBlank { "0.0.0" }
                val body = root.optString("body", null.toString())

                UpdateResult(
                    isUpdateAvailable = isRemoteNewer(currentVersionName, latestTag),
                    latestVersion = latestTag,
                    releaseNotes = body
                )
            } catch (e: Exception) {
                Log.e("UpdateCheck", "Failed to check updates: ${e.message}")
                UpdateResult(false, currentVersionName, null)
            }
        }
    }
}
