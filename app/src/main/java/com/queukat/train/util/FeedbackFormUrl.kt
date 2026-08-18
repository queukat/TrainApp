package com.queukat.train.util

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/** Builds the public feedback URL without exposing station-language preferences. */
object FeedbackFormUrl {
    fun build(
        baseUrl: String,
        uiLocale: Locale,
        appVersion: String,
        androidVersion: String,
    ): String {
        val normalizedBaseUrl = baseUrl.trimEnd('/')
        val uri = URI(normalizedBaseUrl)
        require(uri.scheme.equals("https", ignoreCase = true) && uri.host != null) {
            "Feedback form URL must be an absolute HTTPS URL."
        }
        require(uri.rawQuery == null && uri.rawFragment == null) {
            "Feedback form URL must not include a query or fragment."
        }

        val parameters =
            listOf(
                "lang" to supportedLanguageTag(uiLocale),
                "app_version" to appVersion,
                "android_version" to androidVersion,
            )
        val query = parameters.joinToString("&") { (name, value) -> "${encode(name)}=${encode(value)}" }
        return "$normalizedBaseUrl?$query"
    }

    fun supportedLanguageTag(locale: Locale): String =
        when (locale.language.lowercase(Locale.ROOT)) {
            "ru" -> "ru"
            "cs" -> "cs"
            "sk" -> "sk"
            "sr" -> if (locale.script.equals("Cyrl", ignoreCase = true)) "sr-Cyrl" else "sr-Latn"
            else -> "en"
        }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}
