package com.example.wakt.presentation.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Wakt Gradient Colors
val WaktGradientColors = listOf(
    Color(0xFF667eea), // Deep ocean blue
    Color(0xFF764ba2), // Royal purple 
    Color(0xFFf093fb), // Soft magenta
    Color(0xFFf5576c), // Coral red
    Color(0xFFffeaa7)  // Golden yellow
)

// Reusable gradient brushes
val WaktGradient = Brush.linearGradient(
    colors = WaktGradientColors,
    start = androidx.compose.ui.geometry.Offset(0f, 0f),
    end = androidx.compose.ui.geometry.Offset(1000f, 1000f)
)

val WaktGradientHorizontal = Brush.horizontalGradient(
    colors = WaktGradientColors
)

val WaktGradientVertical = Brush.verticalGradient(
    colors = WaktGradientColors
)