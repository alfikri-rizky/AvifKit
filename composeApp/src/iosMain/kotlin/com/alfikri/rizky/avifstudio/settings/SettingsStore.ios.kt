@file:OptIn(ExperimentalForeignApi::class)

package com.alfikri.rizky.avifstudio.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

private val store: DataStore<Preferences> by lazy {
  val documents =
    NSFileManager.defaultManager.URLForDirectory(
      directory = NSDocumentDirectory,
      inDomain = NSUserDomainMask,
      appropriateForURL = null,
      create = false,
      error = null,
    )
  val path = requireNotNull(documents?.path) { "No documents directory" } + "/$SETTINGS_STORE_FILE"
  PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() })
}

actual fun settingsDataStore(): DataStore<Preferences> = store

actual fun systemLanguageTag(): String = NSLocale.currentLocale.languageCode

/**
 * iOS has no per-app locale API; the supported route is the `AppleLanguages` default, which UIKit
 * and Foundation read at launch. Text already composed keeps the old language until the app is
 * reopened, which is why Settings shows a hint saying so.
 */
actual fun applyAppLanguage(languageTag: String?) {
  val defaults = NSUserDefaults.standardUserDefaults
  if (languageTag.isNullOrBlank() || languageTag == AppLanguage.SYSTEM.tag) {
    defaults.removeObjectForKey("AppleLanguages")
  } else {
    defaults.setObject(listOf(languageTag), forKey = "AppleLanguages")
  }
  defaults.synchronize()
}
