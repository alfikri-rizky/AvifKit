package com.alfikri.rizky.avifstudio.settings

import com.alfikri.rizky.avifstudio.model.Recipe
import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsTest {

  /**
   * The one that actually matters. Android has reported Indonesian as the legacy code `in` since
   * Java 1.0, so a lookup that only knows `id` leaves every Indonesian device on English — the same
   * reason the build mirrors `values-id` into `values-in`.
   */
  @Test
  fun readsIndonesianFromBothTheModernAndLegacyCode() {
    assertEquals(AppLanguage.INDONESIAN, AppLanguage.fromTag("id"))
    assertEquals(AppLanguage.INDONESIAN, AppLanguage.fromTag("in"))
    assertEquals(AppLanguage.INDONESIAN, AppLanguage.fromTag("id-ID"))
    assertEquals(AppLanguage.INDONESIAN, AppLanguage.fromTag("in_ID"))
    assertEquals(AppLanguage.INDONESIAN, AppLanguage.fromTag("ID"))
  }

  @Test
  fun readsEnglishWithOrWithoutARegion() {
    assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"))
    assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en-GB"))
    assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("EN_US"))
  }

  @Test
  fun fallsBackToFollowingTheSystemForAnythingUnrecognised() {
    assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("system"))
    assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(null))
    assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(""))
    assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("fr"))
  }

  @Test
  fun everyLanguageSurvivesBeingStoredAndReadBack() {
    AppLanguage.entries.forEach { assertEquals(it, AppLanguage.fromTag(it.tag)) }
  }

  @Test
  fun everyThemeModeSurvivesBeingStoredAndReadBack() {
    ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromTag(it.tag)) }
  }

  @Test
  fun unknownThemeModeFallsBackToFollowingTheSystem() {
    assertEquals(ThemeMode.SYSTEM, ThemeMode.fromTag(null))
    assertEquals(ThemeMode.SYSTEM, ThemeMode.fromTag("sepia"))
    assertEquals(ThemeMode.DARK, ThemeMode.fromTag("DARK"))
  }

  @Test
  fun everyRecipeSurvivesBeingStoredAndReadBack() {
    Recipe.entries.forEach { assertEquals(it, recipeFromTag(it.name)) }
  }

  /** A preset renamed or removed in a later build must not brick the launch. */
  @Test
  fun unknownStoredRecipeFallsBackToTheDefault() {
    assertEquals(Recipe.WEB_READY, recipeFromTag(null))
    assertEquals(Recipe.WEB_READY, recipeFromTag("SOMETHING_WE_DELETED"))
    assertEquals(Recipe.TO_PNG, recipeFromTag("to_png"))
  }

  @Test
  fun defaultsToFollowingTheSystemOnFirstLaunch() {
    val settings = AppSettings()
    assertEquals(AppLanguage.SYSTEM, settings.language)
    assertEquals(ThemeMode.SYSTEM, settings.themeMode)
    assertEquals(Recipe.WEB_READY, settings.lastRecipe)
  }
}
