package com.example.wakt.presentation.ui.theme

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

// WaktGradient inspired color schemes
private val DarkColorScheme = darkColorScheme(
    primary = WaktGradientColors[4], // Golden yellow for dark theme primary
    onPrimary = WaktGradientColors[1], // Royal purple text on primary
    primaryContainer = WaktGradientColors[1], // Royal purple container
    onPrimaryContainer = WaktGradientColors[4], // Golden yellow text on container
    
    secondary = WaktGradientColors[0], // Deep ocean blue
    onSecondary = WaktGradientColors[4], // Golden yellow text
    secondaryContainer = WaktGradientColors[0].copy(alpha = 0.3f), // Darker ocean blue container
    onSecondaryContainer = WaktGradientColors[4], // Golden yellow text on container
    
    tertiary = WaktGradientColors[3], // Coral red
    onTertiary = Purple80, // White text on tertiary
    tertiaryContainer = WaktGradientColors[3].copy(alpha = 0.3f), // Darker coral container
    onTertiaryContainer = Purple80, // White text on container
    
    background = androidx.compose.ui.graphics.Color(0xFF121212), // Dark background
    onBackground = WaktGradientColors[4], // Golden text
    surface = androidx.compose.ui.graphics.Color(0xFF1E1E1E), // Dark surface
    onSurface = WaktGradientColors[4], // Golden text on surface
    
    surfaceVariant = WaktGradientColors[1].copy(alpha = 0.2f), // Purple tint
    onSurfaceVariant = WaktGradientColors[2], // Soft magenta
    outline = WaktGradientColors[0].copy(alpha = 0.5f) // Ocean blue outline
)

private val LightColorScheme = lightColorScheme(
    primary = WaktGradientColors[1], // Royal purple
    onPrimary = androidx.compose.ui.graphics.Color.White, // White text on primary
    primaryContainer = WaktGradientColors[2].copy(alpha = 0.3f), // Light magenta container
    onPrimaryContainer = WaktGradientColors[1], // Royal purple text on container
    
    secondary = WaktGradientColors[0], // Deep ocean blue
    onSecondary = androidx.compose.ui.graphics.Color.White, // White text
    secondaryContainer = WaktGradientColors[0].copy(alpha = 0.2f), // Light ocean blue container
    onSecondaryContainer = WaktGradientColors[1], // Royal purple text on container
    
    tertiary = WaktGradientColors[3], // Coral red
    onTertiary = androidx.compose.ui.graphics.Color.White, // White text on tertiary
    tertiaryContainer = WaktGradientColors[3].copy(alpha = 0.2f), // Light coral container
    onTertiaryContainer = WaktGradientColors[1], // Royal purple text on container
    
    background = androidx.compose.ui.graphics.Color.White, // White background
    onBackground = WaktGradientColors[1], // Royal purple text
    surface = androidx.compose.ui.graphics.Color.White, // White surface
    onSurface = WaktGradientColors[1], // Royal purple text on surface
    
    surfaceVariant = WaktGradientColors[4].copy(alpha = 0.1f), // Light golden tint
    onSurfaceVariant = WaktGradientColors[0], // Ocean blue text
    outline = WaktGradientColors[1].copy(alpha = 0.5f) // Purple outline
)

@Composable
fun WaktTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
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