package com.alfikri.rizky.avifstudio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.alfikri.rizky.avifkit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher

/**
 * Whether this OS has a photo picker at all.
 *
 * Where it does not, the gallery door is not simply worse — it is the same door: the platform falls
 * back to the document picker, so "Add photos" and "Add files" open one identical screen. The UI
 * drops to a single button rather than offering the same thing twice.
 */
@Composable expect fun isPhotoPickerAvailable(): Boolean

/** The two ways images get into the app, wrapped so screens do not repeat the launcher plumbing. */
class ImagePickers(internal val images: () -> Unit, internal val files: () -> Unit) {
  fun pickImages() = images()

  fun pickFiles() = files()
}

/**
 * A photo picker and a file picker.
 *
 * Both exist because they are genuinely different doors: the photo picker reaches the gallery,
 * which is where the images people want to shrink actually live, while the file picker reaches
 * Downloads and cloud providers — the only way to get at an `.avif` that arrived from the web,
 * which the photo picker will not show on a device that cannot decode it.
 */
@Composable
fun rememberImagePickers(onPicked: (List<PlatformFile>) -> Unit): ImagePickers {
  val imageLauncher =
    rememberFilePickerLauncher(type = FileKitType.Image, mode = FileKitMode.Multiple()) { picked ->
      onPicked(picked.orEmpty())
    }

  val fileLauncher =
    rememberFilePickerLauncher(
      // Bare extensions, no dots. AVIF is first because opening one is a headline use case.
      type = FileKitType.File(listOf("avif", "jpg", "jpeg", "png", "webp", "heic", "heif", "bmp")),
      mode = FileKitMode.Multiple(),
    ) { picked ->
      onPicked(picked.orEmpty())
    }

  return remember(imageLauncher, fileLauncher) {
    ImagePickers(images = { imageLauncher.launch() }, files = { fileLauncher.launch() })
  }
}
