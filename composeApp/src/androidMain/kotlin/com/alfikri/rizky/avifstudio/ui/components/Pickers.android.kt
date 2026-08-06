package com.alfikri.rizky.avifstudio.ui.components

import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * True when `PickVisualMedia` will actually reach a photo picker — the system one from Android 13,
 * the updatable system app on 11 and 12, or the Play services one.
 *
 * When all three are missing the contract still works, but it falls back to `ACTION_OPEN_DOCUMENT`:
 * the same document picker the file button opens, only filtered to images. Verified on an API 24
 * emulator, where both buttons opened the identical "Recent" screen.
 */
@Composable
actual fun isPhotoPickerAvailable(): Boolean {
  val context = LocalContext.current
  return remember(context) { PickVisualMedia.isPhotoPickerAvailable(context) }
}
