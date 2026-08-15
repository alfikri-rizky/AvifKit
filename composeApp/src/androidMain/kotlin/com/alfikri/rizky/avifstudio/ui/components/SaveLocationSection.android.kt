package com.alfikri.rizky.avifstudio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.alfikri.rizky.avifstudio.platform.asSaveLocation
import com.alfikri.rizky.avifstudio.resources.Res
import com.alfikri.rizky.avifstudio.resources.save_location_hint
import com.alfikri.rizky.avifstudio.settings.SaveLocation
import com.anggrayudi.storage.compose.rememberLauncherForFolderPicker
import com.anggrayudi.storage.toStorageFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/**
 * SimpleStorage owns the whole picking half of this: it asks for the storage permission where the
 * API level still needs one, takes the persistable grant, and sends the user back to the volume
 * root when they picked something the system will not grant. None of that is visible here, which is
 * the point of using it.
 */
@Composable
actual fun DefaultSaveLocationSection(
  location: SaveLocation?,
  onLocationChange: (SaveLocation?) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val folderPicker = rememberLauncherForFolderPicker { folder ->
    // Resolving a freshly picked folder's path and name queries the content resolver, so it does
    // not belong on the frame that dismissed the picker.
    scope.launch {
      val picked = withContext(Dispatchers.IO) { folder.toStorageFile(context).asSaveLocation() }
      onLocationChange(picked)
    }
  }

  SaveLocationSectionLayout(
    location = location,
    hint = stringResource(Res.string.save_location_hint),
    onPick = { folderPicker.launch() },
    onClear = { onLocationChange(null) },
  )
}
