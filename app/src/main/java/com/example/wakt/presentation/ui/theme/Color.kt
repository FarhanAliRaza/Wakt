package com.example.wakt.presentation.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Shadcn-inspired Blue Color Palette
object ShadcnColors {
    // Primary Blues
    val Blue50 = Color(0xFFEFF6FF)
    val Blue100 = Color(0xFFDBEAFE)
    val Blue200 = Color(0xFFBFDBFE)
    val Blue300 = Color(0xFF93C5FD)
    val Blue400 = Color(0xFF60A5FA)
    val Blue500 = Color(0xFF3B82F6)  // Primary
    val Blue600 = Color(0xFF2563EB)
    val Blue700 = Color(0xFF1D4ED8)
    val Blue800 = Color(0xFF1E40AF)
    val Blue900 = Color(0xFF1E3A8A)

    // Neutral/Slate (for backgrounds, text)
    val Slate50 = Color(0xFFF8FAFC)
    val Slate100 = Color(0xFFF1F5F9)
    val Slate200 = Color(0xFFE2E8F0)
    val Slate300 = Color(0xFFCBD5E1)
    val Slate400 = Color(0xFF94A3B8)
    val Slate500 = Color(0xFF64748B)
    val Slate600 = Color(0xFF475569)
    val Slate700 = Color(0xFF334155)
    val Slate800 = Color(0xFF1E293B)
    val Slate900 = Color(0xFF0F172A)
    val Slate950 = Color(0xFF020617)

    // Zinc (alternative dark surfaces)
    val Zinc800 = Color(0xFF27272A)
    val Zinc900 = Color(0xFF18181B)
    val Zinc950 = Color(0xFF09090B)

    // Semantic colors
    val Destructive = Color(0xFFEF4444)
    val DestructiveDark = Color(0xFF7F1D1D)
    val Success = Color(0xFF22C55E)
    val Warning = Color(0xFFF59E0B)
}

// Dark Theme Colors
val DarkBackground = ShadcnColors.Slate950
val DarkSurface = ShadcnColors.Slate900
val DarkSurfaceVariant = ShadcnColors.Slate800
val DarkOnSurface = ShadcnColors.Slate100
val DarkOnSurfaceVariant = ShadcnColors.Slate400
val DarkPrimary = ShadcnColors.Blue500
val DarkPrimaryContainer = ShadcnColors.Blue900
val DarkOnPrimary = Color.White
val DarkSecondary = ShadcnColors.Slate700
val DarkOutline = ShadcnColors.Slate700
val DarkOutlineVariant = ShadcnColors.Slate800

// Light Theme Colors
val LightBackground = ShadcnColors.Slate50
val LightSurface = Color.White
val LightSurfaceVariant = ShadcnColors.Slate100
val LightOnSurface = ShadcnColors.Slate900
val LightOnSurfaceVariant = ShadcnColors.Slate600
val LightPrimary = ShadcnColors.Blue600
val LightPrimaryContainer = ShadcnColors.Blue100
val LightOnPrimary = Color.White
val LightSecondary = ShadcnColors.Slate200
val LightOutline = ShadcnColors.Slate300
val LightOutlineVariant = ShadcnColors.Slate200

// Gradient for backward compatibility with existing screens
val WaktGradient = Brush.horizontalGradient(
    colors = listOf(
        ShadcnColors.Blue600,
        ShadcnColors.Blue500
    )
)
