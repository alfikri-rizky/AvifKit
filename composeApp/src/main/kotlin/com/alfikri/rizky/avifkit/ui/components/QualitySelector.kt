package com.alfikri.rizky.avifkit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alfikri.rizky.avifkit.ui.models.QualityPreset

@Composable
fun QualitySelector(
  selectedPreset: QualityPreset,
  onPresetChange: (QualityPreset) -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Text(
        text = "Quality Preset",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 12.dp),
      )

      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        QualityPreset.entries.forEach { preset ->
          QualityPresetItem(
            preset = preset,
            isSelected = selectedPreset == preset,
            onClick = { onPresetChange(preset) },
          )
        }
      }
    }
  }
}

@Composable
private fun QualityPresetItem(
  preset: QualityPreset,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    onClick = onClick,
    modifier = modifier.fillMaxWidth(),
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
          } else {
            MaterialTheme.colorScheme.surfaceVariant
          }
      ),
    shape = RoundedCornerShape(8.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = preset.getDisplayName(),
          style = MaterialTheme.typography.titleSmall,
          color =
            if (isSelected) {
              MaterialTheme.colorScheme.onPrimaryContainer
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
          text = preset.getDescription(),
          style = MaterialTheme.typography.bodySmall,
          color =
            if (isSelected) {
              MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            },
        )
      }

      if (isSelected) {
        RadioButton(
          selected = true,
          onClick = null,
        )
      }
    }
  }
}
