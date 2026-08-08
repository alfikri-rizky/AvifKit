package com.alfikri.rizky.avifstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alfikri.rizky.avifkit.PlatformFile
import com.alfikri.rizky.avifstudio.model.ConversionJob
import com.alfikri.rizky.avifstudio.model.formatBytes
import com.alfikri.rizky.avifstudio.model.formatDimensions
import com.alfikri.rizky.avifstudio.model.formatDuration
import com.alfikri.rizky.avifstudio.resources.Res
import com.alfikri.rizky.avifstudio.resources.converted
import com.alfikri.rizky.avifstudio.resources.dimensions
import com.alfikri.rizky.avifstudio.resources.file_size
import com.alfikri.rizky.avifstudio.resources.original
import com.alfikri.rizky.avifstudio.resources.share
import com.alfikri.rizky.avifstudio.resources.time_taken
import com.alfikri.rizky.avifstudio.resources.view_full_screen
import com.alfikri.rizky.avifstudio.ui.savingsLabel
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultDetailSheet(job: ConversionJob, onDismiss: () -> Unit, onShare: (PlatformFile) -> Unit) {
  val output = job.outputOrNull ?: return
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var fullScreen by remember { mutableStateOf(false) }

  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        text = output.displayName,
        style = MaterialTheme.typography.titleLarge,
        maxLines = 1,
        overflow = TextOverflow.MiddleEllipsis,
      )

      ImagePreview(
        file = output.file,
        maxDimension = 1024,
        modifier =
          Modifier.fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
      )

      Text(
        text = savingsLabel(output.inputBytes, output.sizeBytes),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary,
      )

      DetailRow(
        label = stringResource(Res.string.file_size),
        before = formatBytes(output.inputBytes),
        after = formatBytes(output.sizeBytes),
      )
      DetailRow(
        label = stringResource(Res.string.dimensions),
        before =
          if (output.inputWidth != null && output.inputHeight != null) {
            formatDimensions(output.inputWidth, output.inputHeight)
          } else {
            "—"
          },
        after = formatDimensions(output.width, output.height),
      )
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(Res.string.time_taken), style = MaterialTheme.typography.bodyMedium)
        Text(formatDuration(output.elapsedMillis), style = MaterialTheme.typography.bodyMedium)
      }

      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { fullScreen = true }, modifier = Modifier.weight(1f)) {
          Text(stringResource(Res.string.view_full_screen))
        }
        Button(onClick = { onShare(output.file) }, modifier = Modifier.weight(1f)) {
          Text(stringResource(Res.string.share))
        }
      }
    }
  }

  if (fullScreen) {
    FullScreenImageViewer(file = output.file, onDismiss = { fullScreen = false })
  }
}

@Composable
private fun DetailRow(label: String, before: String, after: String) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, style = MaterialTheme.typography.bodyMedium)
    Text(
      text =
        "${stringResource(Res.string.original)}: $before  →  ${stringResource(Res.string.converted)}: $after",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
