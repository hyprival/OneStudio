package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val OneUiDarkColorScheme = darkColorScheme(
    primary = OneUiBlueDark,
    onPrimary = Color.White,
    primaryContainer = OneUiBlueContainerDark,
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = OneUiCyan,
    onSecondary = Color.Black,
    surface = OneUiSurfaceDark,
    onSurface = Color(0xFFF1F5F9),
    surfaceContainer = OneUiSurfaceContainerDark,
    surfaceContainerHigh = OneUiSurfaceContainerHighDark,
    background = OneUiSurfaceDark,
    onBackground = Color(0xFFF1F5F9),
    outline = Color(0xFF334155),
    error = OneUiCoral
)

private val OneUiLightColorScheme = lightColorScheme(
    primary = OneUiBlueLight,
    onPrimary = Color.White,
    primaryContainer = OneUiBlueContainerLight,
    onPrimaryContainer = Color(0xFF00326A),
    secondary = OneUiBlueLight,
    onSecondary = Color.White,
    surface = OneUiSurfaceLight,
    onSurface = Color(0xFF0F172A),
    surfaceContainer = OneUiSurfaceContainerLight,
    surfaceContainerHigh = OneUiSurfaceContainerHighLight,
    background = OneUiSurfaceLight,
    onBackground = Color(0xFF0F172A),
    outline = Color(0xFFCBD5E1),
    error = OneUiCoral
)

/**
 * Material You / Dynamic Colors implementation for Samsung One UI.
 * On Android 12+ (Samsung One UI 4.0+), automatically extracts colors from the user's
 * wallpaper to style buttons, pills, cards, and highlights seamlessly.
 */
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // Enabled for Material You wallpaper palette
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }
        darkTheme -> OneUiDarkColorScheme
        else -> OneUiLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
