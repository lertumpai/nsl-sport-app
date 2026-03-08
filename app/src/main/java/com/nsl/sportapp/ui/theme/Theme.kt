package com.nsl.sportapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RunningOrange = Color(0xFFFF6B35)
private val RunningOrangeVariant = Color(0xFFE55A2B)
private val WalkingBlue = Color(0xFF2196F3)
private val Surface = Color(0xFF1A1A1A)
private val Background = Color(0xFF121212)

private val DarkColors = darkColorScheme(
    primary = RunningOrange,
    onPrimary = Color.White,
    primaryContainer = RunningOrangeVariant,
    secondary = WalkingBlue,
    onSecondary = Color.White,
    background = Background,
    surface = Surface,
    onBackground = Color.White,
    onSurface = Color.White,
)

private val LightColors = lightColorScheme(
    primary = RunningOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE5D9),
    secondary = WalkingBlue,
    onSecondary = Color.White,
    background = Color(0xFFF5F5F5),
    surface = Color.White,
)

@Composable
fun NSLSportTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
