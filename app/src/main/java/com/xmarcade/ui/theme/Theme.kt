package com.xmarcade.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = MoneroOrange,
    onPrimary = Color.White,
    primaryContainer = MoneroOrangeDark,
    secondary = XmCyan,
    onSecondary = XmNavy,
    background = XmNavy,
    surface = XmSurface,
    surfaceVariant = XmCard,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = DividerDark
)

private val LightScheme = lightColorScheme(
    primary = MoneroOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFF006A6B),
    background = Color(0xFFF6F7F9),
    surface = Color.White,
    surfaceVariant = Color(0xFFEFF2F7),
    onBackground = Color(0xFF0B1426),
    onSurface = Color(0xFF0B1426),
)

@Composable
fun XMArcadeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    secondaryHue: Float? = null, // sliding toggle adjusts secondary color hue
    content: @Composable () -> Unit
) {
    // Allow hue shift for secondary color personalization (settings slider)
    val secondary = if (secondaryHue != null) {
        Color.hsv(secondaryHue, 0.85f, 1f)
    } else XmCyan

    val colorScheme = if (darkTheme) DarkScheme.copy(secondary = secondary)
    else LightScheme.copy(secondary = secondary, primary = MoneroOrange)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
