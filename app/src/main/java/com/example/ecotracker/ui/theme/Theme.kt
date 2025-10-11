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
    primary = VerdeHoja,
    onPrimary = Blanco,
    secondary = VerdeMusgo,
    tertiary = VerdeMenta,
    background = Negro,
    surface = VerdeOscuro,
    onBackground = Blanco,
    onSurface = Blanco,
    outline = VerdeClaro,
    onSecondary = Blanco
)

private val LightColorScheme = lightColorScheme(
    primary = VerdeHoja,
    onPrimary = Blanco,
    secondary = VerdeMusgo,
    tertiary = VerdeMenta,
    background = BeigeClaro,
    surface = Blanco,
    onBackground = MarronTierra,
    onSurface = MarronTierra,
    outline = VerdeClaro,
    onSecondary = Blanco
)

@Composable
fun ECOTRACKERTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
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