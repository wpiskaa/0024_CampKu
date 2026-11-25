package com.example.campku.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = Forest80,
    onPrimary = OnForest80,
    primaryContainer = ForestContainer80,
    onPrimaryContainer = OnForestContainer80,
    secondary = Campfire80,
    onSecondary = OnCampfire80,
    secondaryContainer = CampfireContainer80,
    onSecondaryContainer = Color(0xFFFFDBC8),
    tertiary = Earth80,
    tertiaryContainer = EarthContainer80,
    background = Background80,
    onBackground = OnSurface80,
    surface = Surface80,
    onSurface = OnSurface80,
    surfaceVariant = SurfaceVariant80,
    onSurfaceVariant = OnSurfaceVariant80,
    outlineVariant = OutlineVariant80,
    surfaceContainerLowest = Background80,
    surfaceContainerLow = Surface80,
    surfaceContainer = Surface80,
    surfaceContainerHigh = SurfaceVariant80,
    surfaceContainerHighest = Surface80,
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
    background = Background40,
    onBackground = OnSurface40,
    surface = Surface40,
    onSurface = OnSurface40,
    surfaceVariant = SurfaceVariant40,
    onSurfaceVariant = OnSurfaceVariant40,
    outline = Outline40,
    outlineVariant = OutlineVariant40,
    surfaceContainerLowest = Surface40,
    surfaceContainerLow = Background40,
    surfaceContainer = Background40,
    surfaceContainerHigh = SurfaceVariant40,
    surfaceContainerHighest = Surface40,
)

private val CampKuShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
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
        shapes = CampKuShapes,
        content = content,
    )
}
