package com.queukat.train

import androidx.test.uiautomator.UiDevice
import java.net.HttpURLConnection
import java.net.URL

internal fun UiDevice.wakeAndUnlock() {
    executeShellCommand("input keyevent KEYCODE_WAKEUP")
    executeShellCommand("wm dismiss-keyguard")
    executeShellCommand("input keyevent 82")
    waitForIdle()
}

internal fun liveApiReachable(): Boolean =
    runCatching {
        (URL("https://api.zpcg.me/api/stops").openConnection() as HttpURLConnection).run {
            connectTimeout = 5_000
            readTimeout = 5_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            connect()
            try {
                responseCode in 200..299
            } finally {
                disconnect()
            }
        }
    }.getOrDefault(false)
