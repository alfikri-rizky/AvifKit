package com.alfikri.rizky.avifstudio.settings

import com.alfikri.rizky.avifkit.ChromaSubsample
import com.alfikri.rizky.avifkit.CompressionStrategy
import com.alfikri.rizky.avifstudio.model.ConversionSettings
import com.alfikri.rizky.avifstudio.model.OutputFormat
import com.alfikri.rizky.avifstudio.model.Recipe

/**
 * Which language the UI is drawn in.
 *
 * Adding one means adding an entry here and a `values-<tag>` folder — the Settings screen renders
 * whatever this enum contains, so nothing in the UI has to change.
 */
enum class AppLanguage(val tag: String, val flag: String) {
  /** Follow the device language, falling back to English for anything we do not translate. */
  SYSTEM("system", "\uD83C\uDF10"),
  ENGLISH("en", "\uD83C\uDDEC\uD83C\uDDE7"),
  INDONESIAN("id", "\uD83C\uDDEE\uD83C\uDDE9");

  companion object {
    /**
     * Accepts the legacy ISO codes as well as the modern ones. Android has reported Indonesian as
     * `in` since Java 1.0, so a lookup that only knows `id` silently leaves every Indonesian device
     * on English.
     */
    fun fromTag(tag: String?): AppLanguage {
      val normalized = tag?.trim()?.lowercase()?.substringBefore('-')?.substringBefore('_')
      return when (normalized) {
        "id",
        "in" -> INDONESIAN
        "en" -> ENGLISH
        "system" -> SYSTEM
        else -> SYSTEM
      }
    }
  }
}

enum class ThemeMode(val tag: String, val glyph: String) {
  SYSTEM("system", "\uD83C\uDF13"),
  LIGHT("light", "\u2600\uFE0F"),
  DARK("dark", "\uD83C\uDF19");

  companion object {
    fun fromTag(tag: String?): ThemeMode =
      entries.firstOrNull { it.tag.equals(tag?.trim(), ignoreCase = true) } ?: SYSTEM
  }
}

data class AppSettings(
  val language: AppLanguage = AppLanguage.SYSTEM,
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  /**
   * The recipe picked last time. People convert for the same reason over and over — someone who
   * shrinks screenshots for a wiki does not want to re-pick "Web-ready" every single launch.
   */
  val lastRecipe: Recipe = Recipe.WEB_READY,
  /**
   * The encoder settings as the user last left them, tweaks included. Someone who always needs
   * quality 92 and no resize should not have to walk back into Advanced settings every launch.
   */
  val conversion: ConversionSettings = Recipe.WEB_READY.defaultSettings(),
)

/** Reads a stored recipe name, falling back to the default for anything unrecognised. */
fun recipeFromTag(tag: String?): Recipe =
  Recipe.entries.firstOrNull { it.name.equals(tag?.trim(), ignoreCase = true) } ?: Recipe.WEB_READY

/** Reads a stored output format name, falling back to AVIF for anything unrecognised. */
fun outputFormatFromTag(tag: String?): OutputFormat =
  OutputFormat.entries.firstOrNull { it.name.equals(tag?.trim(), ignoreCase = true) }
    ?: OutputFormat.AVIF

fun subsampleFromTag(tag: String?): ChromaSubsample =
  ChromaSubsample.entries.firstOrNull { it.name.equals(tag?.trim(), ignoreCase = true) }
    ?: ChromaSubsample.YUV420

fun strategyFromTag(tag: String?): CompressionStrategy =
  CompressionStrategy.entries.firstOrNull { it.name.equals(tag?.trim(), ignoreCase = true) }
    ?: CompressionStrategy.SMART
