package com.alfikri.rizky.avifstudio.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfikri.rizky.avifstudio.resources.Res
import com.alfikri.rizky.avifstudio.resources.default_save_location
import com.alfikri.rizky.avifstudio.resources.save_location_ask
import com.alfikri.rizky.avifstudio.resources.save_location_clear
import com.alfikri.rizky.avifstudio.settings.SaveLocation
import org.jetbrains.compose.resources.stringResource

/**
 * Lets the user nominate one folder that Save writes into, instead of being asked every time.
 *
 * Only the picking half is per-platform — SimpleStorage's folder picker on Android, the Files
 * directory picker on iOS — so both actuals draw [SaveLocationSectionLayout] and differ in what
 * happens on a tap.
 */
@Composable
expect fun DefaultSaveLocationSection(
  location: SaveLocation?,
  onLocationChange: (SaveLocation?) -> Unit,
)

/**
 * The shared body of [DefaultSaveLocationSection]. [hint] is passed in rather than read here
 * because it names where folders can live, and that differs per platform.
 */
@Composable
internal fun SaveLocationSectionLayout(
  location: SaveLocation?,
  hint: String,
  onPick: () -> Unit,
  onClear: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
      stringResource(Res.string.default_save_location),
      style = MaterialTheme.typography.titleMedium,
    )

    Surface(
      onClick = onPick,
      modifier = Modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.small,
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 1.dp,
      shadowElevation = 2.dp,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("📁", fontSize = 17.sp)
        Spacer(Modifier.width(10.dp))
        Text(
          text = location?.label ?: stringResource(Res.string.save_location_ask),
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
      }
    }

    Text(
      text = hint,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (location != null) {
      TextButton(onClick = onClear) { Text(stringResource(Res.string.save_location_clear)) }
    }
  }
}
