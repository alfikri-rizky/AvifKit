package com.alfikri.rizky.avifstudio.platform

import androidx.compose.runtime.Composable
import com.alfikri.rizky.avifkit.PlatformFile
import com.alfikri.rizky.avifstudio.settings.SaveLocation

sealed interface ExportResult {
  data class Saved(val count: Int, val location: String) : ExportResult

  data object Cancelled : ExportResult

  data class Failed(val message: String) : ExportResult
}

/**
 * Both exits are permission-free by design: sharing goes through a FileProvider grant on Android
 * and the activity sheet on iOS, and saving asks the user to point at a destination rather than
 * claiming broad storage access.
 */
expect class PlatformActions {

  fun share(files: List<PlatformFile>, mimeType: String)

  /**
   * Writes [files] into the folder chosen in Settings, or asks where to put them when there is
   * none.
   *
   * Asking is also the fallback on both platforms when the remembered folder has since been
   * deleted, unmounted, or had the grant behind it revoked.
   */
  fun export(files: List<PlatformFile>)

  /**
   * Whether the OS itself can read AVIF — Android 12+ and iOS 16+. False means this app is the only
   * way to look at those files on this device, which is worth telling the user.
   */
  val supportsNativeAvifDecoding: Boolean

  /**
   * Whether the OS itself can write AVIF. Separate from [supportsNativeAvifDecoding] because the
   * two directions genuinely diverge: a current iOS does both, while Android reads AVIF from 12 and
   * still has no encoder for it at all.
   */
  val supportsNativeAvifEncoding: Boolean
}

/**
 * @param defaultSaveLocation the folder chosen in Settings, read afresh on every save rather than
 *   captured — changing it in Settings has to take effect without the export plumbing being
 *   rebuilt.
 */
@Composable
expect fun rememberPlatformActions(
  defaultSaveLocation: SaveLocation?,
  onExportResult: (ExportResult) -> Unit,
): PlatformActions
