package com.alfikri.rizky.avifstudio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.alfikri.rizky.avifstudio.platform.asSaveLocation
import com.alfikri.rizky.avifstudio.resources.Res
import com.alfikri.rizky.avifstudio.resources.save_location_hint_ios
import com.alfikri.rizky.avifstudio.settings.SaveLocation
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The Files directory picker, which is what makes a folder outside our sandbox reachable at all:
 * what comes back is security-scoped, and [asSaveLocation] turns it into the bookmark that carries
 * that access across launches.
 */
@Composable
actual fun DefaultSaveLocationSection(
  location: SaveLocation?,
  onLocationChange: (SaveLocation?) -> Unit,
) {
  val scope = rememberCoroutineScope()

  SaveLocationSectionLayout(
    location = location,
    hint = stringResource(Res.string.save_location_hint_ios),
    onPick = {
      scope.launch {
        val folder = FileKit.openDirectoryPicker() ?: return@launch
        // A folder we cannot bookmark is one Save could not reach after this screen closes, so
        // leave whatever was already set alone rather than replacing it with something unusable.
        val picked = runCatching { folder.asSaveLocation() }.getOrNull() ?: return@launch
        onLocationChange(picked)
      }
    },
    onClear = { onLocationChange(null) },
  )
}
