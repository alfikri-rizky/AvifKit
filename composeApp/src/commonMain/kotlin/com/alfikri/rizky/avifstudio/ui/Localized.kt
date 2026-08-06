package com.alfikri.rizky.avifstudio.ui

import androidx.compose.runtime.Composable
import com.alfikri.rizky.avifstudio.model.FailureReason
import com.alfikri.rizky.avifstudio.model.Recipe
import com.alfikri.rizky.avifstudio.model.savingsPercent
import com.alfikri.rizky.avifstudio.resources.Res
import com.alfikri.rizky.avifstudio.resources.appearance
import com.alfikri.rizky.avifstudio.resources.dark
import com.alfikri.rizky.avifstudio.resources.english
import com.alfikri.rizky.avifstudio.resources.failure_encode
import com.alfikri.rizky.avifstudio.resources.failure_not_an_image
import com.alfikri.rizky.avifstudio.resources.failure_out_of_memory
import com.alfikri.rizky.avifstudio.resources.failure_unknown
import com.alfikri.rizky.avifstudio.resources.failure_unreadable
import com.alfikri.rizky.avifstudio.resources.follow_system
import com.alfikri.rizky.avifstudio.resources.indonesian
import com.alfikri.rizky.avifstudio.resources.light
import com.alfikri.rizky.avifstudio.resources.recipe_archive
import com.alfikri.rizky.avifstudio.resources.recipe_archive_desc
import com.alfikri.rizky.avifstudio.resources.recipe_custom
import com.alfikri.rizky.avifstudio.resources.recipe_custom_desc
import com.alfikri.rizky.avifstudio.resources.recipe_fit_size
import com.alfikri.rizky.avifstudio.resources.recipe_fit_size_desc
import com.alfikri.rizky.avifstudio.resources.recipe_smallest
import com.alfikri.rizky.avifstudio.resources.recipe_smallest_desc
import com.alfikri.rizky.avifstudio.resources.recipe_to_jpeg
import com.alfikri.rizky.avifstudio.resources.recipe_to_jpeg_desc
import com.alfikri.rizky.avifstudio.resources.recipe_to_png
import com.alfikri.rizky.avifstudio.resources.recipe_to_png_desc
import com.alfikri.rizky.avifstudio.resources.recipe_to_webp
import com.alfikri.rizky.avifstudio.resources.recipe_to_webp_desc
import com.alfikri.rizky.avifstudio.resources.recipe_web_ready
import com.alfikri.rizky.avifstudio.resources.recipe_web_ready_desc
import com.alfikri.rizky.avifstudio.resources.savings_larger
import com.alfikri.rizky.avifstudio.resources.savings_same
import com.alfikri.rizky.avifstudio.resources.savings_smaller
import com.alfikri.rizky.avifstudio.settings.AppLanguage
import com.alfikri.rizky.avifstudio.settings.ThemeMode
import org.jetbrains.compose.resources.stringResource

/**
 * Model-to-copy mapping, kept in one file so a new recipe or failure reason is a compile error in
 * exactly one place instead of a silently untranslated string somewhere in the UI.
 */
@Composable
fun Recipe.title(): String =
  stringResource(
    when (this) {
      Recipe.WEB_READY -> Res.string.recipe_web_ready
      Recipe.FIT_SIZE_LIMIT -> Res.string.recipe_fit_size
      Recipe.SMALLEST -> Res.string.recipe_smallest
      Recipe.ARCHIVE -> Res.string.recipe_archive
      Recipe.TO_WEBP -> Res.string.recipe_to_webp
      Recipe.TO_JPEG -> Res.string.recipe_to_jpeg
      Recipe.TO_PNG -> Res.string.recipe_to_png
      Recipe.CUSTOM -> Res.string.recipe_custom
    }
  )

@Composable
fun Recipe.description(): String =
  stringResource(
    when (this) {
      Recipe.WEB_READY -> Res.string.recipe_web_ready_desc
      Recipe.FIT_SIZE_LIMIT -> Res.string.recipe_fit_size_desc
      Recipe.SMALLEST -> Res.string.recipe_smallest_desc
      Recipe.ARCHIVE -> Res.string.recipe_archive_desc
      Recipe.TO_WEBP -> Res.string.recipe_to_webp_desc
      Recipe.TO_JPEG -> Res.string.recipe_to_jpeg_desc
      Recipe.TO_PNG -> Res.string.recipe_to_png_desc
      Recipe.CUSTOM -> Res.string.recipe_custom_desc
    }
  )

@Composable
fun FailureReason.label(): String =
  stringResource(
    when (this) {
      FailureReason.UNREADABLE -> Res.string.failure_unreadable
      FailureReason.NOT_AN_IMAGE -> Res.string.failure_not_an_image
      FailureReason.ENCODE_FAILED -> Res.string.failure_encode
      FailureReason.OUT_OF_MEMORY -> Res.string.failure_out_of_memory
      FailureReason.UNKNOWN -> Res.string.failure_unknown
    }
  )

@Composable
fun AppLanguage.label(): String =
  stringResource(
    when (this) {
      AppLanguage.SYSTEM -> Res.string.follow_system
      AppLanguage.ENGLISH -> Res.string.english
      AppLanguage.INDONESIAN -> Res.string.indonesian
    }
  )

@Composable
fun ThemeMode.label(): String =
  stringResource(
    when (this) {
      ThemeMode.SYSTEM -> Res.string.follow_system
      ThemeMode.LIGHT -> Res.string.light
      ThemeMode.DARK -> Res.string.dark
    }
  )

/** `62% smaller`, `8% larger`, or `Same size` — the headline number on every result row. */
@Composable
fun savingsLabel(originalBytes: Long, newBytes: Long): String {
  val percent = savingsPercent(originalBytes, newBytes)
  return when {
    percent > 0 -> stringResource(Res.string.savings_smaller, percent)
    percent < 0 -> stringResource(Res.string.savings_larger, -percent)
    else -> stringResource(Res.string.savings_same)
  }
}

/** Title for the appearance section; exposed here so screens do not import Res directly. */
@Composable fun appearanceTitle(): String = stringResource(Res.string.appearance)
