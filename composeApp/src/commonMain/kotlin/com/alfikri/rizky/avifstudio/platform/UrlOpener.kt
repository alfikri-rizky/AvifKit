package com.alfikri.rizky.avifstudio.platform

/**
 * Opens [url] in the system browser.
 *
 * The app has no network permission and no in-app browser on purpose — handing the URL to the OS
 * keeps it that way, and keeps the "works fully offline" claim on the About card honest.
 */
expect fun openUrl(url: String)

/** The library this app is built on, linked from Settings. */
const val AVIFKIT_REPOSITORY_URL = "https://github.com/alfikri-rizky/AvifKit"
