package com.alfikri.rizky.avifstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alfikri.rizky.avifstudio.model.ConversionJob
import com.alfikri.rizky.avifstudio.model.JobStatus
import com.alfikri.rizky.avifstudio.model.formatBytes
import com.alfikri.rizky.avifstudio.resources.Res
import com.alfikri.rizky.avifstudio.resources.remove
import com.alfikri.rizky.avifstudio.resources.retry
import com.alfikri.rizky.avifstudio.resources.skipped_reason
import com.alfikri.rizky.avifstudio.resources.status_cancelled
import com.alfikri.rizky.avifstudio.resources.status_pending
import com.alfikri.rizky.avifstudio.resources.status_running
import com.alfikri.rizky.avifstudio.ui.label
import com.alfikri.rizky.avifstudio.ui.savingsLabel
import org.jetbrains.compose.resources.stringResource

/** One row per picked image: a preview, its name, where it is up to, and what it became. */
@Composable
fun JobRow(
  job: ConversionJob,
  showRemove: Boolean,
  onRemove: () -> Unit,
  onRetry: () -> Unit,
  onOpenDetail: () -> Unit,
) {
  val done = job.status as? JobStatus.Done
  val failed = job.status as? JobStatus.Failed

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    color =
      if (failed != null) MaterialTheme.colorScheme.errorContainer
      else MaterialTheme.colorScheme.surface,
    // A real shadow rather than a flat tint: rows are the thing being scrolled, and lifting them
    // off the background is what makes the list read as a stack of cards.
    tonalElevation = 1.dp,
    shadowElevation = 2.dp,
    onClick = onOpenDetail,
    enabled = done != null,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box {
        // Preview the output once there is one — seeing the converted image is the reassurance
        // that the number next to it is real.
        Thumbnail(file = done?.output?.file ?: job.source.file)
        StatusBadge(job.status, Modifier.align(Alignment.BottomEnd))
      }

      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = job.source.displayName,
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.MiddleEllipsis,
        )
        StatusLine(job)
      }

      if (failed != null) {
        IconButton(onClick = onRetry) {
          Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.retry))
        }
      }
      if (showRemove) {
        IconButton(onClick = onRemove) {
          Icon(
            Icons.Default.Close,
            contentDescription = stringResource(Res.string.remove),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun StatusLine(job: ConversionJob) {
  when (val status = job.status) {
    JobStatus.Pending ->
      Muted("${formatBytes(job.source.sizeBytes)} · ${stringResource(Res.string.status_pending)}")
    JobStatus.Running -> Muted(stringResource(Res.string.status_running))
    is JobStatus.Done -> {
      Muted("${formatBytes(job.source.sizeBytes)}  →  ${formatBytes(status.output.sizeBytes)}")
      SavingsPill(job.source.sizeBytes, status.output.sizeBytes)
    }
    is JobStatus.Failed -> {
      Text(
        text = status.reason.label(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
      )
      status.detail?.let {
        Text(
          text = it,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    JobStatus.Skipped -> Muted(stringResource(Res.string.skipped_reason))
    JobStatus.Cancelled -> Muted(stringResource(Res.string.status_cancelled))
  }
}

@Composable
private fun Muted(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
  )
}

/** The one number a user actually reads on this row, so it gets a colour and a shape. */
@Composable
private fun SavingsPill(originalBytes: Long, newBytes: Long) {
  val grew = newBytes > originalBytes
  Surface(
    shape = CircleShape,
    color =
      if (grew) MaterialTheme.colorScheme.tertiaryContainer
      else MaterialTheme.colorScheme.secondaryContainer,
  ) {
    Text(
      text = savingsLabel(originalBytes, newBytes),
      style = MaterialTheme.typography.labelMedium,
      color =
        if (grew) MaterialTheme.colorScheme.onTertiaryContainer
        else MaterialTheme.colorScheme.onSecondaryContainer,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
    )
  }
}

/** A small corner badge on the thumbnail, so status reads at a glance while scrolling. */
@Composable
private fun StatusBadge(status: JobStatus, modifier: Modifier = Modifier) {
  val (background, tint, icon) =
    when (status) {
      is JobStatus.Done ->
        Triple(
          MaterialTheme.colorScheme.primary,
          MaterialTheme.colorScheme.onPrimary,
          Icons.Default.Check,
        )
      is JobStatus.Failed ->
        Triple(
          MaterialTheme.colorScheme.error,
          MaterialTheme.colorScheme.onError,
          Icons.Default.Warning,
        )
      JobStatus.Skipped ->
        Triple(
          MaterialTheme.colorScheme.tertiary,
          MaterialTheme.colorScheme.onTertiary,
          Icons.Default.Info,
        )
      JobStatus.Running -> {
        // The spinner has to be smaller than the disc behind it. At the same size the disc fills
        // the ring's hole and the whole badge reads as a white blob rather than a spinner.
        Surface(
          modifier = modifier.size(22.dp),
          shape = CircleShape,
          color = MaterialTheme.colorScheme.surface,
          shadowElevation = 2.dp,
        ) {
          Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
              modifier = Modifier.size(13.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
        return
      }
      else -> return
    }

  Box(
    modifier.size(20.dp).background(background, CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
  }
}
