package com.example.ecotracker.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = GreenForest,
    onPrimary = TextPrimary,
    secondary = GreenLime,
    tertiary = SkyBlue,
    background = BlackPrimary,
    surface = SurfaceDark,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = Charcoal
)

private val LightColorScheme = lightColorScheme(
    primary = GreenForest,
    onPrimary = Color.White,
    secondary = GreenLime,
    tertiary = SkyBlue
)

@Composable
fun ECOTRACKERTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}