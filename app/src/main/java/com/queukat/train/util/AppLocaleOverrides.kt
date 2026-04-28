package com.queukat.train.util

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList

fun applyForcedAppLocale(context: Context, localeTag: String) {
    if (localeTag.isBlank()) return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val localeManager = context.getSystemService(LocaleManager::class.java) ?: return
    localeManager.applicationLocales = LocaleList.forLanguageTags(localeTag)
}
