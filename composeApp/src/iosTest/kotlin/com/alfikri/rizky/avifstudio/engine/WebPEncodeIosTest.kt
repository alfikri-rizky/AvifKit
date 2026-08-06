@file:OptIn(ExperimentalForeignApi::class)

package com.alfikri.rizky.avifstudio.engine

import com.alfikri.rizky.avifstudio.model.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIRectFill

/**
 * WebP is the one format whose encoder is not part of iOS — it comes from the SDWebImageWebPCoder
 * pod. A green compile only proves the bindings exist, so this runs the encoder and looks at the
 * bytes: if the pod ever stops being linked, or the encoder silently falls back to another format,
 * this fails.
 */
class WebPEncodeIosTest {

  @Test
  fun encodesRealWebPBytes() = runTest {
    val bytes = ImageCodec().encode(solidImage(), OutputFormat.WEBP, quality = 80)

    assertTrue(bytes.size > 16, "encoder returned ${bytes.size} bytes")
    // A WebP file is a RIFF container whose form type is "WEBP" at offset 8. JPEG or PNG bytes
    // would fail both of these.
    assertEquals("RIFF", bytes.copyOfRange(0, 4).decodeToString())
    assertEquals("WEBP", bytes.copyOfRange(8, 12).decodeToString())
  }

  private fun solidImage(): UIImage {
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(64.0, 48.0), true, 1.0)
    UIColor.redColor.setFill()
    UIRectFill(CGRectMake(0.0, 0.0, 64.0, 48.0))
    val image = checkNotNull(UIGraphicsGetImageFromCurrentImageContext())
    UIGraphicsEndImageContext()
    return image
  }
}
