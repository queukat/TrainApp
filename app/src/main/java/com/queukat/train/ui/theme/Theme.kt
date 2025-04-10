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
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = CustomPrimary,
    onPrimary = CustomSurface,
    secondary = CustomAccentYellow,
    onSecondary = CustomTextPrimary,
    background = CustomSurface,
    surface = CustomSurface,
    onSurface = CustomTextPrimary,
    surfaceVariant = CustomBackground
)


private val DarkColorScheme = darkColorScheme(
    primary = CustomPrimary,
    onPrimary = CustomSurface,
    secondary = CustomAccentYellow,
    onSecondary = CustomTextPrimary,
    background = CustomTextPrimary, //  #121212
    surface = CustomTextPrimary,    //  #1E1E1E
)

@Composable
fun TrainAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    //     Material3 (Android 12+)
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        //   dynamicColor ( )
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(LocalView.current.context)
            else dynamicLightColorScheme(LocalView.current.context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    //   - ( )
    val view = LocalView.current
    SideEffect {
        val activity = view.context as? android.app.Activity ?: return@SideEffect
        //   -
        activity.window?.statusBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)

        //   WindowCompat  ""  "ё"  
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // . Type.kt,   
        content = content
    )
}
