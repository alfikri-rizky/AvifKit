package com.alfikri.rizky.avifstudio.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.alfikri.rizky.avifstudio.model.ConversionSettings
import com.alfikri.rizky.avifstudio.model.Recipe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Reads and writes the user's preferences.
 *
 * An interface so the ViewModel can be constructed in a test without a real DataStore file.
 */
interface SettingsRepository {
  val settings: Flow<AppSettings>

  suspend fun setLanguage(language: AppLanguage)

  suspend fun setThemeMode(mode: ThemeMode)

  /** `null` goes back to asking for a destination on every save. */
  suspend fun setDefaultSaveLocation(location: SaveLocation?)

  suspend fun setLastRecipe(recipe: Recipe)

  suspend fun setConversionSettings(settings: ConversionSettings)
}

/**
 * Persists [AppSettings] in a Preferences DataStore.
 *
 * Reads never throw: a corrupt or unreadable store falls back to defaults, because losing a theme
 * preference is not a reason to fail a launch.
 */
class SettingsStore(private val dataStore: DataStore<Preferences> = settingsDataStore()) :
  SettingsRepository {

  override val settings: Flow<AppSettings> =
    dataStore.data
      .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
      .map { preferences ->
        AppSettings(
          language = AppLanguage.fromTag(preferences[KEY_LANGUAGE]),
          themeMode = ThemeMode.fromTag(preferences[KEY_THEME]),
          defaultSaveLocation = preferences.readSaveLocation(),
          lastRecipe = recipeFromTag(preferences[KEY_RECIPE]),
          conversion = preferences.readConversionSettings(),
        )
      }

  override suspend fun setLanguage(language: AppLanguage) {
    runCatching { dataStore.edit { it[KEY_LANGUAGE] = language.tag } }
  }

  override suspend fun setThemeMode(mode: ThemeMode) {
    runCatching { dataStore.edit { it[KEY_THEME] = mode.tag } }
  }

  override suspend fun setDefaultSaveLocation(location: SaveLocation?) {
    runCatching {
      dataStore.edit { preferences ->
        if (location == null) {
          preferences.remove(KEY_SAVE_LOCATION_ID)
          preferences.remove(KEY_SAVE_LOCATION_LABEL)
        } else {
          preferences[KEY_SAVE_LOCATION_ID] = location.id
          preferences[KEY_SAVE_LOCATION_LABEL] = location.label
        }
      }
    }
  }

  override suspend fun setLastRecipe(recipe: Recipe) {
    runCatching { dataStore.edit { it[KEY_RECIPE] = recipe.name } }
  }

  override suspend fun setConversionSettings(settings: ConversionSettings) {
    runCatching {
      dataStore.edit { preferences ->
        preferences[KEY_FORMAT] = settings.outputFormat.name
        preferences[KEY_QUALITY] = settings.quality
        preferences[KEY_SPEED] = settings.speed
        preferences[KEY_SUBSAMPLE] = settings.subsample.name
        preferences[KEY_ALPHA] = settings.alphaQuality
        preferences[KEY_LOSSLESS] = settings.lossless
        // Preferences has no nullable primitives, so "no limit" is stored as -1 rather than
        // as a missing key — a missing key is indistinguishable from a first launch.
        preferences[KEY_MAX_DIMENSION] = settings.maxDimension ?: NO_LIMIT.toInt()
        preferences[KEY_TARGET_SIZE] = settings.targetSizeBytes ?: NO_LIMIT
        preferences[KEY_STRATEGY] = settings.strategy.name
        preferences[KEY_SKIP_IF_LARGER] = settings.skipIfLarger
      }
    }
  }

  private fun Preferences.readSaveLocation(): SaveLocation? {
    val id = this[KEY_SAVE_LOCATION_ID]?.takeIf { it.isNotBlank() } ?: return null
    // The label is only ever displayed, so a store written by an older build that never saved one
    // is worth showing with the id rather than dropping the location the user actually chose.
    return SaveLocation(id, this[KEY_SAVE_LOCATION_LABEL]?.takeIf { it.isNotBlank() } ?: id)
  }

  private fun Preferences.readConversionSettings(): ConversionSettings {
    val default = recipeFromTag(this[KEY_RECIPE]).defaultSettings()
    val format = this[KEY_FORMAT] ?: return default
    return ConversionSettings(
      outputFormat = outputFormatFromTag(format),
      quality = this[KEY_QUALITY] ?: default.quality,
      speed = this[KEY_SPEED] ?: default.speed,
      subsample = subsampleFromTag(this[KEY_SUBSAMPLE]),
      alphaQuality = this[KEY_ALPHA] ?: default.alphaQuality,
      lossless = this[KEY_LOSSLESS] ?: default.lossless,
      maxDimension = this[KEY_MAX_DIMENSION]?.takeIf { it > 0 },
      targetSizeBytes = this[KEY_TARGET_SIZE]?.takeIf { it > 0 },
      strategy = strategyFromTag(this[KEY_STRATEGY]),
      skipIfLarger = this[KEY_SKIP_IF_LARGER] ?: default.skipIfLarger,
    )
  }

  private companion object {
    val KEY_LANGUAGE = stringPreferencesKey("language")
    val KEY_THEME = stringPreferencesKey("theme_mode")
    val KEY_SAVE_LOCATION_ID = stringPreferencesKey("default_save_location_id")
    val KEY_SAVE_LOCATION_LABEL = stringPreferencesKey("default_save_location_label")
    val KEY_RECIPE = stringPreferencesKey("last_recipe")
    val KEY_FORMAT = stringPreferencesKey("output_format")
    val KEY_QUALITY = intPreferencesKey("quality")
    val KEY_SPEED = intPreferencesKey("speed")
    val KEY_SUBSAMPLE = stringPreferencesKey("subsample")
    val KEY_ALPHA = intPreferencesKey("alpha_quality")
    val KEY_LOSSLESS = booleanPreferencesKey("lossless")
    val KEY_MAX_DIMENSION = intPreferencesKey("max_dimension")
    val KEY_TARGET_SIZE = longPreferencesKey("target_size")
    val KEY_STRATEGY = stringPreferencesKey("strategy")
    val KEY_SKIP_IF_LARGER = booleanPreferencesKey("skip_if_larger")
    const val NO_LIMIT = -1L
  }
}

expect fun settingsDataStore(): DataStore<Preferences>

/** File name shared by both platforms so the store is found in the same place on each. */
internal const val SETTINGS_STORE_FILE = "avifstudio.preferences_pb"

/**
 * The device's language code, e.g. `en` or `id`. Used to resolve [AppLanguage.SYSTEM] for display
 * in Settings; the actual resource lookup is done by the platform locale itself.
 */
expect fun systemLanguageTag(): String

/**
 * Applies an in-app language override at the platform level.
 *
 * Compose Resources picks its `values-*` folder from what the platform reports as the current
 * locale, and its `LocalComposeEnvironment` override is library-internal — so the only way to force
 * a language is to change the platform locale itself.
 *
 * @param languageTag `null` or `"system"` clears the override and follows the device.
 */
expect fun applyAppLanguage(languageTag: String?)
