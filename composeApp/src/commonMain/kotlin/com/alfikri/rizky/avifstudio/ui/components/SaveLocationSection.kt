package com.alfikri.rizky.avifstudio.ui.components

import androidx.compose.runtime.Composable
import com.alfikri.rizky.avifstudio.settings.SaveLocation

/**
 * Lets the user nominate one folder that Save writes into, instead of being asked every time.
 *
 * Draws nothing on iOS. The Files export sheet is the only way out of the sandbox there, and it has
 * no notion of a folder an app can be pointed at ahead of time — so the setting would be a control
 * that changes nothing. It lands there when there is something behind it.
 */
@Composable
expect fun DefaultSaveLocationSection(
  location: SaveLocation?,
  onLocationChange: (SaveLocation?) -> Unit,
)
