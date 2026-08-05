package com.alfikri.rizky.avifstudio.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileNamingTest {

  @Test
  fun replacesTheExtensionWithTheOutputFormat() {
    assertEquals("photo.avif", FileNaming.outputName("photo.JPG", OutputFormat.AVIF))
    assertEquals("photo.jpg", FileNaming.outputName("photo.avif", OutputFormat.JPEG))
    assertEquals("photo.png", FileNaming.outputName("photo.webp", OutputFormat.PNG))
  }

  @Test
  fun keepsEverythingBeforeTheFinalDot() {
    assertEquals(
      "my.holiday.photo.avif",
      FileNaming.outputName("my.holiday.photo.heic", OutputFormat.AVIF),
    )
  }

  @Test
  fun givesAnExtensionToNamesThatHaveNone() {
    assertEquals("IMG_0042.avif", FileNaming.outputName("IMG_0042", OutputFormat.AVIF))
  }

  @Test
  fun keepsSpacesBecauseEverySaveTargetAccceptsThem() {
    assertEquals("My Photo.avif", FileNaming.outputName("My Photo.jpg", OutputFormat.AVIF))
  }

  @Test
  fun replacesCharactersThatWouldBreakASaveTarget() {
    assertEquals("a_b_c.avif", FileNaming.outputName("a/b:c.jpg", OutputFormat.AVIF))
  }

  @Test
  fun fallsBackToAPlaceholderWhenNothingUsableIsLeft() {
    assertEquals("image.avif", FileNaming.outputName("///", OutputFormat.AVIF))
  }

  @Test
  fun leavesAUniqueNameAlone() {
    assertEquals("photo.avif", FileNaming.uniqueName("photo.avif", setOf("other.avif")))
  }

  /** Picking IMG_0042.jpg from two folders is routine; the second must not overwrite the first. */
  @Test
  fun numbersDuplicatesBeforeTheExtension() {
    assertEquals("photo (2).avif", FileNaming.uniqueName("photo.avif", setOf("photo.avif")))
    assertEquals(
      "photo (3).avif",
      FileNaming.uniqueName("photo.avif", setOf("photo.avif", "photo (2).avif")),
    )
  }

  /** SAF and the Files app both treat these as the same file, so the check has to as well. */
  @Test
  fun treatsDifferentCasingAsTheSameName() {
    assertEquals("Photo (2).avif", FileNaming.uniqueName("Photo.avif", setOf("photo.avif")))
  }

  @Test
  fun numbersDuplicatesThatHaveNoExtension() {
    assertEquals("README (2)", FileNaming.uniqueName("README", setOf("README")))
  }

  @Test
  fun readsBaseNameAndExtension() {
    assertEquals("a.b", FileNaming.baseName("a.b.jpg"))
    assertEquals("README", FileNaming.baseName("README"))
    assertEquals("jpg", FileNaming.extensionOf("a.b.JPG"))
    assertEquals("", FileNaming.extensionOf("README"))
  }

  @Test
  fun sanitiseNeverLeavesATrailingDot() {
    assertTrue(!FileNaming.sanitize("weird.").endsWith("."))
  }
}
