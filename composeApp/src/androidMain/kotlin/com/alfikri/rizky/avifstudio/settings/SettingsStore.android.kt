package com.alfikri.rizky.avifstudio.settings

import android.app.LocaleManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.alfikri.rizky.avifstudio.platform.AppContext
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.path
import java.util.Locale
import okio.Path.Companion.toPath

private val store: DataStore<Preferences> by lazy {
  // FileKit's files dir is the app's private storage on both platforms, and on Android it is
  // File-backed, so `path` is a real filesystem path rather than a content URI.
  val file = (FileKit.filesDir / SETTINGS_STORE_FILE).path
  PreferenceDataStoreFactory.createWithPath(produceFile = { file.toPath() })
}

actual fun settingsDataStore(): DataStore<Preferences> = store

actual fun systemLanguageTag(): String = Locale.getDefault().language

actual fun applyAppLanguage(languageTag: String?) {
  val context = AppContext.applicationContext ?: return
  // Below Android 14 the platform still expects the legacy codes for these three languages, and
  // silently ignores the modern one.
  val resolved =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      when (languageTag) {
        "id" -> "in"
        "he" -> "iw"
        "yi" -> "ji"
        else -> languageTag
      }
    } else {
      languageTag
    }

  val followSystem = resolved.isNullOrBlank() || resolved == AppLanguage.SYSTEM.tag

  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    val localeManager = context.getSystemService(LocaleManager::class.java) ?: return
    localeManager.applicationLocales =
      if (followSystem) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(resolved)
    return
  }

  val locale =
    if (followSystem) {
      Resources.getSystem().configuration.locales[0]
    } else {
      Locale.forLanguageTag(resolved!!)
    }
  Locale.setDefault(locale)

  @Suppress("DEPRECATION")
  val configuration =
    Configuration(context.resources.configuration).apply {
      setLocale(locale)
      setLocales(LocaleList(locale))
    }
  @Suppress("DEPRECATION")
  context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
}
