package com.farhanaliraza.wakt.presentation.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Shadcn-inspired Dark Color Scheme
private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = ShadcnColors.Blue100,

    secondary = DarkSecondary,
    onSecondary = DarkOnSurface,
    secondaryContainer = ShadcnColors.Slate800,
    onSecondaryContainer = ShadcnColors.Slate200,

    tertiary = ShadcnColors.Blue400,
    onTertiary = Color.White,
    tertiaryContainer = ShadcnColors.Blue900,
    onTertiaryContainer = ShadcnColors.Blue100,

    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,

    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,

    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,

    error = ShadcnColors.Destructive,
    onError = Color.White,
    errorContainer = ShadcnColors.DestructiveDark,
    onErrorContainer = Color(0xFFFECACA)
)

// Shadcn-inspired Light Color Scheme
private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = ShadcnColors.Blue900,

    secondary = LightSecondary,
    onSecondary = LightOnSurface,
    secondaryContainer = ShadcnColors.Slate100,
    onSecondaryContainer = ShadcnColors.Slate800,

    tertiary = ShadcnColors.Blue500,
    onTertiary = Color.White,
    tertiaryContainer = ShadcnColors.Blue100,
    onTertiaryContainer = ShadcnColors.Blue900,

    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,

    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,

    outline = LightOutline,
    outlineVariant = LightOutlineVariant,

    error = ShadcnColors.Destructive,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = ShadcnColors.DestructiveDark
)

@Composable
fun WaktTheme(
    darkTheme: Boolean = true, // Dark theme by default
    dynamicColor: Boolean = false, // Disable dynamic colors to use our custom scheme
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
