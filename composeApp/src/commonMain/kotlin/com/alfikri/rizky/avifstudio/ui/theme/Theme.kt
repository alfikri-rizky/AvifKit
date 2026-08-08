package com.alfikri.rizky.avifstudio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alfikri.rizky.avifstudio.settings.ThemeMode

// A hand-written scheme rather than Material You: the app has to look identical on iOS, where
// there is no dynamic colour to follow, and a converter that changes personality between the two
// platforms reads as two different apps.
private val Indigo = Color(0xFF4B57D8)
private val IndigoLight = Color(0xFFBEC2FF)
private val Teal = Color(0xFF0F7C6C)
private val TealLight = Color(0xFF7BD9C6)
private val Amber = Color(0xFF8A5100)
private val AmberLight = Color(0xFFFFB870)

private val LightScheme =
  lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E1FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF9DF2DF),
    onSecondaryContainer = Color(0xFF00201B),
    tertiary = Amber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCC0),
    onTertiaryContainer = Color(0xFF2D1600),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    outline = Color(0xFF777680),
    outlineVariant = Color(0xFFC7C5D0),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
  )

private val DarkScheme =
  darkColorScheme(
    primary = IndigoLight,
    onPrimary = Color(0xFF1A2597),
    primaryContainer = Color(0xFF333EBE),
    onPrimaryContainer = Color(0xFFE0E1FF),
    secondary = TealLight,
    onSecondary = Color(0xFF003730),
    secondaryContainer = Color(0xFF005046),
    onSecondaryContainer = Color(0xFF9DF2DF),
    tertiary = AmberLight,
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF693C00),
    onTertiaryContainer = Color(0xFFFFDCC0),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE5E1E6),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE5E1E6),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    outline = Color(0xFF918F9A),
    outlineVariant = Color(0xFF46464F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
  )

/** Resolves [ThemeMode.SYSTEM] against the OS setting; the other two win outright. */
@Composable
fun isDarkTheme(mode: ThemeMode): Boolean =
  when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
  }

@Composable
fun AvifStudioTheme(mode: ThemeMode, content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = if (isDarkTheme(mode)) DarkScheme else LightScheme,
    typography = avifStudioTypography(),
    shapes = AvifStudioShapes,
    content = content,
  )
}

/** Softer than Material's defaults — this is a photo app, and photos sit in rounded frames. */
private val AvifStudioShapes =
  Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
  )
