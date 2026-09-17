package com.xmarcade.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** HSL (h 0-360, s/l %) → Color. Powers the Secondary-color slider. */
fun hsl(h: Int, s: Int, l: Int): Color {
  val hh = ((h % 360) + 360) % 360 / 360f
  val ss = s / 100f; val ll = l / 100f
  fun f(n: Int): Float {
    val k = (n + hh * 12) % 12
    val a = ss * minOf(ll, 1 - ll)
    return ll - a * maxOf(-1f, minOf(k - 3, 9 - k, 1f))
  }
  return Color(f(0), f(8), f(4))
}

@Immutable
data class XMExtra(
  val text2: Color, val text3: Color, val line: Color, val line2: Color,
  val surface2: Color, val surface3: Color, val primaryDim: Color,
  val zap: Color, val ok: Color, val warn: Color, val live: Color, val xmr: Color,
)

val LocalXM = staticCompositionLocalOf {
  XMExtra(Color.Gray, Color.Gray, Color.Gray, Color.Gray, Color.Gray, Color.Gray, Color.Gray, Color.Yellow, Color.Green, Color.Yellow, Color.Red, Color.Red)
}

private fun darkScheme(accent: Color): ColorScheme = darkColorScheme(
  primary = Color(0xFF8B7BFF), onPrimary = Color.White,
  secondary = accent, onSecondary = Color(0xFF0B0D11),
  background = Color(0xFF08090C), onBackground = Color(0xFFF2F3F7),
  surface = Color(0xFF12141A), onSurface = Color(0xFFF2F3F7),
  surfaceVariant = Color(0xFF191C23), onSurfaceVariant = Color(0xFF98A0AF),
  surfaceContainer = Color(0xFF22262F),
  outline = Color(0x1FFFFFFF), outlineVariant = Color(0x24FFFFFF),
  error = Color(0xFFFF5470), onError = Color.White,
)

private fun lightScheme(accent: Color): ColorScheme = lightColorScheme(
  primary = Color(0xFF5B48F0), onPrimary = Color.White,
  secondary = accent, onSecondary = Color.White,
  background = Color(0xFFF6F5F2), onBackground = Color(0xFF14161B),
  surface = Color.White, onSurface = Color(0xFF14161B),
  surfaceVariant = Color(0xFFF3F2EE), onSurfaceVariant = Color(0xFF5A6070),
  surfaceContainer = Color(0xFFE9E7E1),
  outline = Color(0x14111516), outlineVariant = Color(0x29111516),
  error = Color(0xFFE11D48), onError = Color.White,
)

private fun darkExtra() = XMExtra(
  text2 = Color(0xFF98A0AF), text3 = Color(0xFF6A7280),
  line = Color(0x12FFFFFF), line2 = Color(0x24FFFFFF),
  surface2 = Color(0xFF191C23), surface3 = Color(0xFF22262F),
  primaryDim = Color(0x298B7BFF),
  zap = Color(0xFFFFD166), ok = Color(0xFF3DDC97), warn = Color(0xFFFF8A4C),
  live = Color(0xFFFF5470), xmr = Color(0xFFED4357),
)

private fun lightExtra() = XMExtra(
  text2 = Color(0xFF5A6070), text3 = Color(0xFF8A90A0),
  line = Color(0x14111516), line2 = Color(0x29111516),
  surface2 = Color(0xFFF3F2EE), surface3 = Color(0xFFE9E7E1),
  primaryDim = Color(0x1A5B48F0),
  zap = Color(0xFFB45309), ok = Color(0xFF059669), warn = Color(0xFFEA580C),
  live = Color(0xFFE11D48), xmr = Color(0xFFED4357),
)

val XMShapes = Shapes(
  small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
  medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
  large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
  extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

private val XMTypo = Typography(
  headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 34.sp),
  headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold),
  titleLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
  titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
  bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
  bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
  labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
  labelSmall = TextStyle(fontSize = 12.sp),
)

@Composable
fun XMTheme(theme: String, accentHue: Int, content: @Composable () -> Unit) {
  val dark = theme != "light" && (theme == "dark" || isSystemInDarkTheme())
  val accent = hsl(accentHue, 85, if (dark) 55 else 40)
  CompositionLocalProvider(LocalXM provides if (dark) darkExtra() else lightExtra()) {
    MaterialTheme(
      colorScheme = if (dark) darkScheme(accent) else lightScheme(accent),
      shapes = XMShapes, typography = XMTypo, content = content,
    )
  }
}

val Mono = FontFamily.Monospace
