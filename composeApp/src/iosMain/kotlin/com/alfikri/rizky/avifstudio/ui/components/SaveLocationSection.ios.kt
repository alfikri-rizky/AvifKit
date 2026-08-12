package com.alfikri.rizky.avifstudio.ui.components

import androidx.compose.runtime.Composable
import com.alfikri.rizky.avifstudio.settings.SaveLocation

/** Nothing to draw here yet — see the expect declaration for why. */
@Composable
actual fun DefaultSaveLocationSection(
  location: SaveLocation?,
  onLocationChange: (SaveLocation?) -> Unit,
) = Unit
