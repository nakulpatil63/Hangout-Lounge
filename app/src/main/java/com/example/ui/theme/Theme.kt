package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// Dark theme scheme modified for a premium Frosted Slate feel
private val DarkColorScheme =
  darkColorScheme(
    primary = GlassIndigoAccent,
    secondary = GlassIndigoLight,
    tertiary = GlassEmeraldGreen,
    background = GlassBgBase,
    surface = GlassFillLow,
    onBackground = GlassTextWhite,
    onSurface = GlassTextWhite
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false, // Set to false to force our gorgeous Frosted theme
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> DarkColorScheme // Force dark aesthetic for that premium glassmorphism movie-night vibe
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

/**
 * Renders the authentic Frosted Glass dark background containing deep-space indigo & purple glowing radial spots.
 */
fun Modifier.frostedGlassBackground(): Modifier = this.drawBehind {
  // Draw base slate dark color
  drawRect(color = Color(0xFF0A0A0F))

  // Glow Spot 1: Top-Left Indigo Ambient Light
  drawCircle(
    brush = Brush.radialGradient(
      colors = listOf(Color(0x55312E81), Color.Transparent),
      center = androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.15f),
      radius = size.width * 0.9f
    ),
    center = androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.15f),
    radius = size.width * 0.9f
  )

  // Glow Spot 2: Bottom-Right Deep Purple Ambient Light
  drawCircle(
    brush = Brush.radialGradient(
      colors = listOf(Color(0x441E1B4B), Color.Transparent),
      center = androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.85f),
      radius = size.width * 1.1f
    ),
    center = androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.85f),
    radius = size.width * 1.1f
  )
}

/**
 * Applies a translucent glassmorphic look to any container with optional custom shapes.
 */
fun Modifier.frostedGlassCard(
  shape: Shape = RoundedCornerShape(24.dp),
  backgroundColor: Color = GlassFillLow,
  borderColor: Color = GlassBorderLow
): Modifier = this
  .clip(shape)
  .background(backgroundColor)
  .border(width = 1.dp, color = borderColor, shape = shape)
