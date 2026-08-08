package com.alfikri.rizky.avifstudio.platform

/**
 * No cap. iOS has no per-app heap ceiling of the kind Android imposes — an app on the oldest device
 * this ships to still gets hundreds of megabytes before jetsam intervenes, and a full-resolution
 * photo is tens.
 */
actual fun deviceImageDimensionCap(): Int? = null
