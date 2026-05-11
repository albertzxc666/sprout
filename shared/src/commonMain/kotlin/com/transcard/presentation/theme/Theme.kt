package com.transcard.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightScheme = lightColorScheme(
    primary = AppColors.LightPrimary,
    onPrimary = AppColors.LightOnPrimary,
    primaryContainer = AppColors.LightPrimary.copy(alpha = 0.12f),
    onPrimaryContainer = AppColors.LightPrimary,
    secondary = AppColors.LightPrimary,
    onSecondary = AppColors.LightOnPrimary,
    background = AppColors.LightBackground,
    onBackground = AppColors.LightTextPrimary,
    surface = AppColors.LightSurface,
    onSurface = AppColors.LightTextPrimary,
    surfaceVariant = AppColors.LightSurfaceElevated,
    onSurfaceVariant = AppColors.LightTextSecondary,
    surfaceContainer = AppColors.LightSurface,
    surfaceContainerHigh = AppColors.LightSurfaceElevated,
    error = AppColors.LightError,
    onError = AppColors.LightSurface,
    errorContainer = AppColors.LightError.copy(alpha = 0.12f),
    onErrorContainer = AppColors.LightError,
    outline = AppColors.LightDivider,
    outlineVariant = AppColors.LightSeparator
)

private val DarkScheme = darkColorScheme(
    primary = AppColors.DarkPrimary,
    onPrimary = AppColors.DarkOnPrimary,
    primaryContainer = AppColors.DarkPrimary.copy(alpha = 0.18f),
    onPrimaryContainer = AppColors.DarkPrimary,
    secondary = AppColors.DarkPrimary,
    onSecondary = AppColors.DarkOnPrimary,
    background = AppColors.DarkBackground,
    onBackground = AppColors.DarkTextPrimary,
    surface = AppColors.DarkSurface,
    onSurface = AppColors.DarkTextPrimary,
    surfaceVariant = AppColors.DarkSurfaceElevated,
    onSurfaceVariant = AppColors.DarkTextSecondary,
    surfaceContainer = AppColors.DarkSurface,
    surfaceContainerHigh = AppColors.DarkSurfaceElevated,
    error = AppColors.DarkError,
    onError = AppColors.DarkBackground,
    errorContainer = AppColors.DarkError.copy(alpha = 0.18f),
    onErrorContainer = AppColors.DarkError,
    outline = AppColors.DarkDivider,
    outlineVariant = AppColors.DarkSeparator
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp)
)

@Composable
fun TransCardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
