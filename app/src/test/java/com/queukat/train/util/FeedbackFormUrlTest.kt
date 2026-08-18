package com.queukat.train.util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedbackFormUrlTest {
    @Test
    fun supportedLanguageTagMapsAppLocalesAndFallsBackToEnglish() {
        assertEquals("en", FeedbackFormUrl.supportedLanguageTag(Locale.US))
        assertEquals("ru", FeedbackFormUrl.supportedLanguageTag(Locale.forLanguageTag("ru-RU")))
        assertEquals("cs", FeedbackFormUrl.supportedLanguageTag(Locale.forLanguageTag("cs-CZ")))
        assertEquals("sk", FeedbackFormUrl.supportedLanguageTag(Locale.forLanguageTag("sk-SK")))
        assertEquals("sr-Cyrl", FeedbackFormUrl.supportedLanguageTag(Locale.forLanguageTag("sr-Cyrl-RS")))
        assertEquals("sr-Latn", FeedbackFormUrl.supportedLanguageTag(Locale.forLanguageTag("sr-Latn-RS")))
        assertEquals("en", FeedbackFormUrl.supportedLanguageTag(Locale.forLanguageTag("de-DE")))
    }

    @Test
    fun buildUsesUiLocaleAndPercentEncodesMetadata() {
        assertEquals(
            "https://feedback.example.workers.dev?lang=sr-Cyrl&app_version=1.0%20beta&android_version=14%2FUP1A",
            FeedbackFormUrl.build(
                baseUrl = "https://feedback.example.workers.dev",
                uiLocale = Locale.forLanguageTag("sr-Cyrl-RS"),
                appVersion = "1.0 beta",
                androidVersion = "14/UP1A",
            ),
        )
    }
}
