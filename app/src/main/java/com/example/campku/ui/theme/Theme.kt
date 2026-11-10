package com.example.campku.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Forest80,
    onPrimary = OnForest80,
    primaryContainer = ForestContainer80,
    onPrimaryContainer = OnForestContainer80,
    secondary = Campfire80,
    onSecondary = OnCampfire80,
    secondaryContainer = CampfireContainer80,
    tertiary = Earth80,
    tertiaryContainer = EarthContainer80,
    background = Surface80,
    onBackground = OnSurface80,
    surface = Surface80,
    onSurface = OnSurface80,
)

private val LightColorScheme = lightColorScheme(
    primary = Forest40,
    onPrimary = OnForest40,
    primaryContainer = ForestContainer40,
    onPrimaryContainer = OnForestContainer40,
    secondary = Campfire40,
    onSecondary = OnCampfire40,
    secondaryContainer = CampfireContainer40,
    onSecondaryContainer = OnCampfireContainer40,
    tertiary = Earth40,
    tertiaryContainer = EarthContainer40,
    background = Surface40,
    onBackground = OnSurface40,
    surface = Surface40,
    onSurface = OnSurface40,
)

/** Tema CampKu. Warna dinamis dimatikan agar identitas hijau-camping selalu konsisten. */
@Composable
fun CampKuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
