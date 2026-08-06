package com.alfikri.rizky.avifstudio.platform

/**
 * Longest edge this device can decode without running out of memory, or `null` where memory is not
 * the binding constraint.
 *
 * Decoding is what fails first, not encoding: a 4000x3000 photo is 48 MB as an ARGB_8888 bitmap,
 * and an API 24 emulator hands an app a 48 MB heap in total, so the conversion dies before the
 * encoder ever sees a pixel. A cap turns that into a sub-sampled decode, because
 * [com.alfikri.rizky.avifstudio.engine.ImageCodec] derives `inSampleSize` from it.
 *
 * This only ever fills in a *default* for presets that ask for the original size. Anything the user
 * picks in Advanced settings stands, including "Original size".
 */
expect fun deviceImageDimensionCap(): Int?
