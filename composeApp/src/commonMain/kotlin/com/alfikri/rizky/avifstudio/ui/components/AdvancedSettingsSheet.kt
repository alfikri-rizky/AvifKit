package com.alfikri.rizky.avifstudio.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alfikri.rizky.avifkit.ChromaSubsample
import com.alfikri.rizky.avifkit.CompressionStrategy
import com.alfikri.rizky.avifstudio.model.ConversionSettings
import com.alfikri.rizky.avifstudio.model.OutputFormat
import com.alfikri.rizky.avifstudio.model.formatBytes
import com.alfikri.rizky.avifstudio.resources.Res
import com.alfikri.rizky.avifstudio.resources.advanced_title
import com.alfikri.rizky.avifstudio.resources.alpha_quality
import com.alfikri.rizky.avifstudio.resources.chroma_subsampling
import com.alfikri.rizky.avifstudio.resources.encode_speed
import com.alfikri.rizky.avifstudio.resources.encode_speed_hint
import com.alfikri.rizky.avifstudio.resources.keep_original_size
import com.alfikri.rizky.avifstudio.resources.lossless
import com.alfikri.rizky.avifstudio.resources.lossless_hint
import com.alfikri.rizky.avifstudio.resources.output_format
import com.alfikri.rizky.avifstudio.resources.quality
import com.alfikri.rizky.avifstudio.resources.resize_longest_edge
import com.alfikri.rizky.avifstudio.resources.skip_if_larger
import com.alfikri.rizky.avifstudio.resources.skip_if_larger_hint
import com.alfikri.rizky.avifstudio.resources.strategy
import com.alfikri.rizky.avifstudio.resources.strategy_smart
import com.alfikri.rizky.avifstudio.resources.strategy_smart_hint
import com.alfikri.rizky.avifstudio.resources.strategy_strict
import com.alfikri.rizky.avifstudio.resources.strategy_strict_hint
import com.alfikri.rizky.avifstudio.resources.target_size
import org.jetbrains.compose.resources.stringResource

/**
 * Every encoder knob, for the people who want them.
 *
 * Options that the current format ignores are hidden rather than disabled — a greyed-out chroma
 * subsampling row on a PNG export teaches nobody anything.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdvancedSettingsSheet(
  settings: ConversionSettings,
  onDismiss: () -> Unit,
  onChange: (ConversionSettings) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
    Column(
      modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      Text(
        stringResource(Res.string.advanced_title),
        style = MaterialTheme.typography.headlineSmall,
      )

      Section(stringResource(Res.string.output_format)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutputFormat.entries.forEach { format ->
            FilterChip(
              selected = settings.outputFormat == format,
              onClick = { onChange(settings.copy(outputFormat = format)) },
              label = { Text(format.label) },
            )
          }
        }
      }

      // PNG is lossless by definition and AVIF in lossless mode ignores it too.
      if (!settings.outputFormat.isLossless && !settings.lossless) {
        Section("${stringResource(Res.string.quality)} · ${settings.quality}") {
          Slider(
            value = settings.quality.toFloat(),
            onValueChange = { onChange(settings.copy(quality = it.toInt())) },
            valueRange = 1f..100f,
          )
        }
      }

      if (settings.outputFormat == OutputFormat.AVIF) {
        Section("${stringResource(Res.string.encode_speed)} · ${settings.speed}") {
          Slider(
            value = settings.speed.toFloat(),
            onValueChange = { onChange(settings.copy(speed = it.toInt())) },
            valueRange = 0f..10f,
            steps = 9,
          )
          Hint(stringResource(Res.string.encode_speed_hint))
        }

        if (!settings.lossless) {
          Section(stringResource(Res.string.chroma_subsampling)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              ChromaSubsample.entries.forEach { subsample ->
                FilterChip(
                  selected = settings.subsample == subsample,
                  onClick = { onChange(settings.copy(subsample = subsample)) },
                  label = { Text(subsample.name) },
                )
              }
            }
          }

          Section("${stringResource(Res.string.alpha_quality)} · ${settings.alphaQuality}") {
            Slider(
              value = settings.alphaQuality.toFloat(),
              onValueChange = { onChange(settings.copy(alphaQuality = it.toInt())) },
              valueRange = 1f..100f,
            )
          }
        }

        ToggleRow(
          title = stringResource(Res.string.lossless),
          hint = stringResource(Res.string.lossless_hint),
          checked = settings.lossless,
          onCheckedChange = { onChange(settings.copy(lossless = it)) },
        )

        Section(stringResource(Res.string.target_size)) {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
              selected = settings.targetSizeBytes == null,
              onClick = { onChange(settings.copy(targetSizeBytes = null)) },
              label = { Text("—") },
            )
            ConversionSettings.SIZE_TARGET_CHOICES.forEach { bytes ->
              FilterChip(
                selected = settings.targetSizeBytes == bytes,
                onClick = { onChange(settings.copy(targetSizeBytes = bytes)) },
                label = { Text(formatBytes(bytes)) },
              )
            }
          }
        }

        if (settings.hasSizeTarget) {
          Section(stringResource(Res.string.strategy)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilterChip(
                selected = settings.strategy == CompressionStrategy.SMART,
                onClick = { onChange(settings.copy(strategy = CompressionStrategy.SMART)) },
                label = { Text(stringResource(Res.string.strategy_smart)) },
              )
              FilterChip(
                selected = settings.strategy == CompressionStrategy.STRICT,
                onClick = { onChange(settings.copy(strategy = CompressionStrategy.STRICT)) },
                label = { Text(stringResource(Res.string.strategy_strict)) },
              )
            }
            Hint(
              if (settings.strategy == CompressionStrategy.SMART) {
                stringResource(Res.string.strategy_smart_hint)
              } else {
                stringResource(Res.string.strategy_strict_hint)
              }
            )
          }
        }
      }

      Section(stringResource(Res.string.resize_longest_edge)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          ConversionSettings.DIMENSION_CHOICES.forEach { dimension ->
            FilterChip(
              selected = settings.maxDimension == dimension,
              onClick = { onChange(settings.copy(maxDimension = dimension)) },
              label = {
                Text(dimension?.let { "$it px" } ?: stringResource(Res.string.keep_original_size))
              },
            )
          }
        }
      }

      ToggleRow(
        title = stringResource(Res.string.skip_if_larger),
        hint = stringResource(Res.string.skip_if_larger_hint),
        checked = settings.skipIfLarger,
        onCheckedChange = { onChange(settings.copy(skipIfLarger = it)) },
      )
    }
  }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    content()
  }
}

@Composable
private fun Hint(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

@Composable
private fun ToggleRow(
  title: String,
  hint: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleSmall)
      Hint(hint)
    }
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}
