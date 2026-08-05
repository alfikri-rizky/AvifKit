package com.alfikri.rizky.avifstudio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.alfikri.rizky.avifstudio.resources.Res
import com.alfikri.rizky.avifstudio.resources.poppins_bold
import com.alfikri.rizky.avifstudio.resources.poppins_medium
import com.alfikri.rizky.avifstudio.resources.poppins_regular
import com.alfikri.rizky.avifstudio.resources.poppins_semibold
import org.jetbrains.compose.resources.Font

/**
 * Poppins, bundled rather than borrowed from the OS.
 *
 * A shared-UI app that falls back to the system font renders as Roboto on Android and SF on iOS —
 * different rhythm, different metrics, two apps that only look related. Four static weights are
 * shipped instead of the variable file because Compose renders a variable font at its default
 * instance, which would make every weight identical.
 */
@Composable
fun poppins(): FontFamily =
  FontFamily(
    Font(Res.font.poppins_regular, FontWeight.Normal),
    Font(Res.font.poppins_medium, FontWeight.Medium),
    Font(Res.font.poppins_semibold, FontWeight.SemiBold),
    Font(Res.font.poppins_bold, FontWeight.Bold),
  )

/**
 * Poppins runs optically large and wide, so the scale is tightened: display sizes lose a little
 * letter spacing, and body sizes gain line height to stay comfortable in Indonesian, where words
 * are noticeably longer than their English equivalents.
 */
@Composable
fun avifStudioTypography(): Typography {
  val family = poppins()
  val base = MaterialTheme.typography
  return Typography(
    displaySmall =
      base.displaySmall.copy(
        fontFamily = family,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-1).sp,
      ),
    headlineLarge =
      base.headlineLarge.copy(
        fontFamily = family,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
      ),
    headlineMedium =
      base.headlineMedium.copy(
        fontFamily = family,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
      ),
    headlineSmall =
      base.headlineSmall.copy(
        fontFamily = family,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
      ),
    titleLarge = base.titleLarge.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = family, lineHeight = 24.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = family, lineHeight = 21.sp),
    bodySmall = base.bodySmall.copy(fontFamily = family, lineHeight = 18.sp),
    labelLarge = base.labelLarge.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = family, fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontFamily = family, fontWeight = FontWeight.Medium),
  )
}

/** The oversized savings figure on the results card, which is the app's one hero number. */
@Composable
fun heroNumberStyle(): TextStyle =
  TextStyle(
    fontFamily = poppins(),
    fontWeight = FontWeight.Bold,
    fontSize = 40.sp,
    lineHeight = 44.sp,
    letterSpacing = (-1.5).sp,
  )
