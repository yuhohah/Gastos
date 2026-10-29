package com.example.gastos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF86DDC6), onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF144F43), onPrimaryContainer = Color(0xFFC1F6E5),
    secondary = Color(0xFFAFCBC1), secondaryContainer = Color(0xFF304B42),
    background = Color(0xFF101512), surface = Color(0xFF101512),
    surfaceVariant = Color(0xFF3F4943), onSurface = Color(0xFFE0E5DF),
    onSurfaceVariant = Color(0xFFBFC9C1), outline = Color(0xFF89948C)
)
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF176B56), onPrimary = Color.White,
    primaryContainer = Color(0xFFD1F3E5), onPrimaryContainer = Color(0xFF073B2D),
    secondary = Color(0xFF4D6358), secondaryContainer = Color(0xFFDFECE3),
    background = Color(0xFFF7FAF6), surface = Color(0xFFF7FAF6),
    surfaceVariant = Color(0xFFE1E9E2), onSurface = Color(0xFF17221C),
    onSurfaceVariant = Color(0xFF526159), outline = Color(0xFF74857B)
)

@Composable
fun GastosTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography, content = content)
}
