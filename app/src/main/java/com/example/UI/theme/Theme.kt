package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val KikoDarkColorScheme = darkColorScheme(
    primary = KikoCyan,
    onPrimary = KikoVoid,
    primaryContainer = KikoCyanDim,
    onPrimaryContainer = KikoCyanGlow,
    secondary = KikoAzure,
    onSecondary = KikoTextPrimary,
    secondaryContainer = KikoSurfaceBright,
    onSecondaryContainer = KikoAzureSoft,
    tertiary = KikoAmber,
    onTertiary = KikoVoid,
    background = KikoVoid,
    onBackground = KikoTextPrimary,
    surface = KikoSurface,
    onSurface = KikoTextPrimary,
    surfaceVariant = KikoSurfaceElevated,
    onSurfaceVariant = KikoTextSecondary,
    outline = KikoOutline,
    outlineVariant = KikoCyanDim,
    error = KikoCrimson,
    onError = KikoTextPrimary,
)

val KikoShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun KikoAiTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = KikoDarkColorScheme,
        typography = KikoTypography,
        shapes = KikoShapes,
        content = content,
    )
}
