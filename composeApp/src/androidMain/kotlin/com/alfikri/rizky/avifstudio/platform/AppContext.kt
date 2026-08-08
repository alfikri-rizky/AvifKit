package com.alfikri.rizky.avifstudio.platform

import android.content.Context

/**
 * The application context, for the few Android APIs that need one from code with no composition and
 * no Activity in scope (the per-app locale override).
 *
 * Deliberately holds only the *application* context — an Activity stored here would leak on every
 * rotation. Populated once from [android.app.Application.onCreate], so it is set before any
 * composition runs.
 */
object AppContext {
  @Volatile
  var applicationContext: Context? = null
    private set

  fun install(context: Context) {
    applicationContext = context.applicationContext
  }
}
