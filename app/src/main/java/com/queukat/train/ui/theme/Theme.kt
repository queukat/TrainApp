package com.queukat.train.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme =
    lightColorScheme(
        primary = CustomPrimary,
        onPrimary = CustomSurface,
        secondary = CustomAccentYellow,
        onSecondary = CustomTextPrimary,
        background = CustomSurface,
        surface = CustomSurface,
        onSurface = CustomTextPrimary,
        surfaceVariant = CustomBackground,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = CustomPrimary,
        onPrimary = CustomSurface,
        secondary = CustomAccentYellow,
        onSecondary = CustomTextPrimary,
        background = CustomTextPrimary,
        surface = CustomTextPrimary,
    )

@Composable
fun TrainAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) {
                    dynamicDarkColorScheme(LocalView.current.context)
                } else {
                    dynamicLightColorScheme(LocalView.current.context)
                }
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    val view = LocalView.current
    SideEffect {
        val activity = view.context as? android.app.Activity ?: return@SideEffect
        val window = activity.window ?: return@SideEffect

        // На Android 9/10 popup-меню (DropdownMenu) часто ломается при edge-to-edge.
        // Поэтому edge-to-edge включаем только на Android 11+.
        val edgeToEdge = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

        if (edgeToEdge) {
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.setDecorFitsSystemWindows(window, false)
        } else {
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.setDecorFitsSystemWindows(window, true)
        }

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
