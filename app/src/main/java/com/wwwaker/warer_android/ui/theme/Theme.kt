package com.wwwaker.warer_android.ui.theme

import android.app.Activity
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

private val LightColorScheme = lightColorScheme(
    primary = WarerPrimary,
    onPrimary = WarerOnPrimary,
    primaryContainer = WarerPrimaryContainer,
    onPrimaryContainer = WarerOnPrimaryContainer,
    secondary = WarerSecondary,
    onSecondary = WarerOnSecondary,
    secondaryContainer = WarerSecondaryContainer,
    onSecondaryContainer = WarerOnSecondaryContainer,
    tertiary = WarerTertiary,
    onTertiary = WarerOnTertiary,
    tertiaryContainer = WarerTertiaryContainer,
    onTertiaryContainer = WarerOnTertiaryContainer,
    error = WarerError,
    onError = WarerOnError,
    errorContainer = WarerErrorContainer,
    onErrorContainer = WarerOnErrorContainer,
    background = WarerBackground,
    onBackground = WarerOnBackground,
    surface = WarerSurface,
    onSurface = WarerOnSurface,
    surfaceVariant = WarerSurfaceVariant,
    onSurfaceVariant = WarerOnSurfaceVariant,
    outline = WarerOutline,
    outlineVariant = WarerOutlineVariant,
)

private val DarkColorScheme = darkColorScheme(
    primary = WarerDarkPrimary,
    onPrimary = WarerDarkOnPrimary,
    primaryContainer = WarerDarkPrimaryContainer,
    onPrimaryContainer = WarerDarkOnPrimaryContainer,
    secondary = WarerDarkSecondary,
    onSecondary = WarerDarkOnSecondary,
    secondaryContainer = WarerDarkSecondaryContainer,
    onSecondaryContainer = WarerDarkOnSecondaryContainer,
    tertiary = WarerDarkTertiary,
    onTertiary = WarerDarkOnTertiary,
    tertiaryContainer = WarerDarkTertiaryContainer,
    onTertiaryContainer = WarerDarkOnTertiaryContainer,
    error = WarerDarkError,
    onError = WarerDarkOnError,
    errorContainer = WarerDarkErrorContainer,
    onErrorContainer = WarerDarkOnErrorContainer,
    background = WarerDarkBackground,
    onBackground = WarerDarkOnBackground,
    surface = WarerDarkSurface,
    onSurface = WarerDarkOnSurface,
    surfaceVariant = WarerDarkSurfaceVariant,
    onSurfaceVariant = WarerDarkOnSurfaceVariant,
    outline = WarerDarkOutline,
    outlineVariant = WarerDarkOutlineVariant,
)

@Composable
fun WarerAndroidTheme(
    themeMode: String = "system",
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
