package com.alfikri.rizky.avifstudio

import android.app.Application
import com.alfikri.rizky.avifstudio.platform.AppContext

/**
 * Exists for one reason: the per-app locale override needs a Context from code that has no Activity
 * and no composition in scope, and it has to be available before the first frame is composed.
 */
class AvifStudioApp : Application() {
  override fun onCreate() {
    super.onCreate()
    AppContext.install(this)
  }
}
