package com.example.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val HudDarkColorScheme = darkColorScheme(
    primary = HudCyan,
    onPrimary = HudBackgroundDeep,
    primaryContainer = HudSurfaceBright,
    onPrimaryContainer = HudCyan,
    secondary = HudMagenta,
    onSecondary = HudBackgroundDeep,
    secondaryContainer = HudSurfaceCard,
    onSecondaryContainer = HudMagenta,
    tertiary = HudLime,
    onTertiary = HudBackgroundDeep,
    tertiaryContainer = HudSurfaceCard,
    onTertiaryContainer = HudLime,
    background = HudBackgroundDeep,
    onBackground = HudTextPrimary,
    surface = HudBackgroundElevated,
    onSurface = HudTextPrimary,
    surfaceVariant = HudSurfaceCard,
    onSurfaceVariant = HudTextSecondary,
    outline = HudBorderDim,
    error = HudError,
    onError = HudBackgroundDeep
)

val HudShapes = Shapes(
    extraSmall = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
    small = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
    medium = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
    large = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp),
    extraLarge = CutCornerShape(topStart = 22.dp, bottomEnd = 22.dp)
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = HudDarkColorScheme,
        typography = Typography,
        shapes = HudShapes,
        content = content
    )
}
