package com.alfikri.rizky.avifstudio.platform

/**
 * The app has no network permission and no in-app browser on purpose — handing the URL to the OS
 * keeps it that way, and keeps the "works fully offline" claim on the About card honest.
 */
expect fun openUrl(url: String)

const val AVIFKIT_REPOSITORY_URL = "https://github.com/alfikri-rizky/AvifKit"
