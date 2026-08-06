package com.alfikri.rizky.avifstudio.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfikri.rizky.avifkit.PlatformFile
import com.alfikri.rizky.avifstudio.model.ConversionJob
import com.alfikri.rizky.avifstudio.model.ConversionSettings
import com.alfikri.rizky.avifstudio.model.Recipe
import com.alfikri.rizky.avifstudio.model.formatBytes
import com.alfikri.rizky.avifstudio.resources.Res
import com.alfikri.rizky.avifstudio.resources.add_files
import com.alfikri.rizky.avifstudio.resources.add_more
import com.alfikri.rizky.avifstudio.resources.add_photos
import com.alfikri.rizky.avifstudio.resources.cancel
import com.alfikri.rizky.avifstudio.resources.clear_all
import com.alfikri.rizky.avifstudio.resources.convert_count
import com.alfikri.rizky.avifstudio.resources.converting
import com.alfikri.rizky.avifstudio.resources.count_converted
import com.alfikri.rizky.avifstudio.resources.count_failed
import com.alfikri.rizky.avifstudio.resources.count_kept
import com.alfikri.rizky.avifstudio.resources.empty_body
import com.alfikri.rizky.avifstudio.resources.empty_title
import com.alfikri.rizky.avifstudio.resources.image_count
import com.alfikri.rizky.avifstudio.resources.nothing_saved
import com.alfikri.rizky.avifstudio.resources.original
import com.alfikri.rizky.avifstudio.resources.privacy_note
import com.alfikri.rizky.avifstudio.resources.progress_of
import com.alfikri.rizky.avifstudio.resources.results_title
import com.alfikri.rizky.avifstudio.resources.save
import com.alfikri.rizky.avifstudio.resources.share
import com.alfikri.rizky.avifstudio.resources.tagline
import com.alfikri.rizky.avifstudio.resources.total_saved
import com.alfikri.rizky.avifstudio.ui.components.AdvancedSettingsSheet
import com.alfikri.rizky.avifstudio.ui.components.JobRow
import com.alfikri.rizky.avifstudio.ui.components.RecipePicker
import com.alfikri.rizky.avifstudio.ui.components.ResultDetailSheet
import com.alfikri.rizky.avifstudio.ui.components.rememberImagePickers
import com.alfikri.rizky.avifstudio.ui.theme.heroNumberStyle
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  state: StudioUiState,
  contentPadding: PaddingValues,
  onAddSources: (List<PlatformFile>) -> Unit,
  onRemoveSource: (String) -> Unit,
  onClearAll: () -> Unit,
  onSelectRecipe: (Recipe) -> Unit,
  onUpdateSettings: (ConversionSettings) -> Unit,
  onStart: () -> Unit,
  onCancel: () -> Unit,
  onRetry: (String) -> Unit,
  onShare: (List<PlatformFile>) -> Unit,
  onExport: (List<PlatformFile>) -> Unit,
) {
  val pickers = rememberImagePickers(onPicked = onAddSources)
  var showAdvanced by remember { mutableStateOf(false) }
  var detailJob by remember { mutableStateOf<ConversionJob?>(null) }

  val running = state.phase == BatchPhase.RUNNING
  val finished = state.phase == BatchPhase.FINISHED

  Column(Modifier.fillMaxSize().padding(contentPadding)) {
    AnimatedVisibility(visible = running, enter = fadeIn(), exit = fadeOut()) {
      val progress by animateFloatAsState(state.progress, tween(300), label = "batch")
      LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(CircleShape),
        // Material 3's default track picks up the secondary container, which against this
        // palette is a bright mint that reads louder than the progress itself.
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        // The "stop indicator" dot at the far end looks like a stray artefact on a thin bar.
        drawStopIndicator = {},
        gapSize = 0.dp,
      )
    }

    if (!state.hasSources) {
      // Outside the list so it can centre in the space left over, instead of hugging the top of a
      // scroll container that has nothing else in it.
      Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp), Alignment.Center) {
        EmptyState()
      }
    }

    LazyColumn(
      modifier =
        if (state.hasSources) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth(),
      contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      if (state.hasSources && finished) {
        item { SummaryCard(state) }
      }

      if (state.hasSources && !finished) {
        item {
          RecipePicker(
            selected = state.recipe,
            settings = state.settings,
            enabled = !running,
            onSelect = onSelectRecipe,
            onUpdateSettings = onUpdateSettings,
            onOpenAdvanced = { showAdvanced = true },
          )
        }
      }

      if (state.hasSources) {
        item {
          Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text =
                when {
                  running ->
                    stringResource(Res.string.progress_of, state.completedCount, state.jobs.size)
                  finished -> stringResource(Res.string.results_title)
                  else ->
                    pluralStringResource(Res.plurals.image_count, state.jobs.size, state.jobs.size)
                },
              style = MaterialTheme.typography.titleMedium,
            )
            if (!running) {
              TextButton(onClick = onClearAll) { Text(stringResource(Res.string.clear_all)) }
            }
          }
        }
      }

      items(state.jobs, key = { it.source.id }) { job ->
        JobRow(
          job = job,
          showRemove = !running,
          onRemove = { onRemoveSource(job.source.id) },
          onRetry = { onRetry(job.source.id) },
          onOpenDetail = { detailJob = job },
        )
      }

      if (state.hasSources && !running) {
        item {
          OutlinedButton(onClick = pickers::pickImages, shape = MaterialTheme.shapes.small) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.add_more))
          }
        }
      }
    }

    BottomBar(
      state = state,
      onPickImages = pickers::pickImages,
      onPickFiles = pickers::pickFiles,
      onStart = onStart,
      onCancel = onCancel,
      onShare = { onShare(state.exportableFiles) },
      onExport = { onExport(state.exportableFiles) },
    )
  }

  if (showAdvanced) {
    AdvancedSettingsSheet(
      settings = state.settings,
      onDismiss = { showAdvanced = false },
      onChange = onUpdateSettings,
    )
  }

  detailJob?.let { job ->
    ResultDetailSheet(
      job = job,
      onDismiss = { detailJob = null },
      onShare = { onShare(listOf(it)) },
    )
  }
}

@Composable
private fun EmptyState() {
  Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    HeroMark()
    Spacer(Modifier.height(28.dp))
    Text(
      text = stringResource(Res.string.tagline),
      style = MaterialTheme.typography.headlineSmall,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(Modifier.height(20.dp))
    Text(
      text = stringResource(Res.string.empty_title),
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(4.dp))
    Text(
      text = stringResource(Res.string.empty_body),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
      Text(
        text = stringResource(Res.string.privacy_note),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        textAlign = TextAlign.Center,
      )
    }
  }
}

/**
 * A big frame shrinking into a small one — drawn rather than shipped as an asset, so it inherits
 * the theme's colours, needs no localisation, and adds nothing to the download.
 */
@Composable
private fun HeroMark() {
  Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
    Box(
      Modifier.size(132.dp)
        .clip(RoundedCornerShape(36.dp))
        .background(
          Brush.linearGradient(
            listOf(
              MaterialTheme.colorScheme.primaryContainer,
              MaterialTheme.colorScheme.secondaryContainer,
            )
          )
        )
    )
    Surface(
      modifier = Modifier.align(Alignment.BottomEnd).size(72.dp),
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.primary,
      shadowElevation = 8.dp,
    ) {
      Box(contentAlignment = Alignment.Center) {
        Text(
          text = "AVIF",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onPrimary,
        )
      }
    }
  }
}

@Composable
private fun SummaryCard(state: StudioUiState) {
  val summary = state.summary
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.primaryContainer,
    // The one hero element on the screen, so it sits highest.
    shadowElevation = 6.dp,
  ) {
    Column(
      Modifier.background(
          Brush.linearGradient(
            listOf(
              MaterialTheme.colorScheme.primaryContainer,
              MaterialTheme.colorScheme.secondaryContainer,
            )
          )
        )
        .fillMaxWidth()
        .padding(22.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text =
          if (summary.savedBytes > 0) {
            stringResource(Res.string.total_saved, formatBytes(summary.savedBytes))
          } else {
            stringResource(Res.string.nothing_saved)
          },
        style =
          if (summary.savedBytes > 0) heroNumberStyle() else MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
      )

      if (summary.inputBytes > 0) {
        SavingsBar(inputBytes = summary.inputBytes, outputBytes = summary.outputBytes)
      }

      val parts = buildList {
        if (summary.succeeded > 0)
          add(stringResource(Res.string.count_converted, summary.succeeded))
        if (summary.skipped > 0) add(stringResource(Res.string.count_kept, summary.skipped))
        if (summary.failed > 0) add(stringResource(Res.string.count_failed, summary.failed))
      }
      Text(
        text = parts.joinToString("  ·  "),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
      )
    }
  }
}

/** Before and after as a bar, because "5.7 MB saved" lands harder when you can see the gap. */
@Composable
private fun SavingsBar(inputBytes: Long, outputBytes: Long) {
  val ratio = outputBytes.toFloat() / inputBytes.toFloat()
  // A sliver rather than nothing: a 99% saving should still read as a bar, not an empty track.
  val fraction = if (ratio.isNaN()) 1f else ratio.coerceIn(0.02f, 1f)
  val animated by animateFloatAsState(fraction, tween(600), label = "savings")

  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Box(
      Modifier.fillMaxWidth()
        .height(10.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.18f))
    ) {
      Box(
        Modifier.fillMaxWidth(animated)
          .height(10.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primary)
      )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(
        text = "${stringResource(Res.string.original)} ${formatBytes(inputBytes)}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
      )
      Text(
        text = formatBytes(outputBytes),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
      )
    }
  }
}

@Composable
private fun BottomBar(
  state: StudioUiState,
  onPickImages: () -> Unit,
  onPickFiles: () -> Unit,
  onStart: () -> Unit,
  onCancel: () -> Unit,
  onShare: () -> Unit,
  onExport: () -> Unit,
) {
  // An explicit gradient rather than Surface(shadowElevation): a rectangular elevation shadow
  // against a near-white background renders almost invisibly here, and this has to read the same
  // on both platforms. Drawn as a sibling above the bar so it always separates the footer from
  // whatever is scrolling underneath it.
  //
  // Black, not onSurface. A scrim must always darken, and onSurface is a *content* token that
  // flips with the theme — in dark mode it is near-white, so this drew a white glow instead of a
  // shadow. Dark mode gets no scrim at all: there the bar is already lighter than the page thanks
  // to the tonal elevation below, which is how Material separates surfaces without shadows.
  if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
    Box(
      Modifier.fillMaxWidth()
        .height(10.dp)
        .background(
          Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.07f)))
        )
    )
  }

  Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 20.dp, vertical = 14.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Keyed on "is there anything to act on?" before phase, so no state can strand the user
      // without a way back to the pickers — an empty FINISHED batch used to show Save/Share only.
      when {
        !state.hasSources -> {
          Button(
            onClick = onPickImages,
            modifier = Modifier.weight(1f).height(54.dp),
            shape = MaterialTheme.shapes.small,
          ) {
            // Emoji rather than a Material icon: material-icons-core has no picture or folder
            // glyph, and the app already speaks emoji for recipes, themes and languages.
            Text("\uD83D\uDDBC\uFE0F", fontSize = 17.sp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.add_photos))
          }
          OutlinedButton(
            onClick = onPickFiles,
            modifier = Modifier.weight(1f).height(54.dp),
            shape = MaterialTheme.shapes.small,
          ) {
            Text("\uD83D\uDCC1", fontSize = 17.sp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.add_files))
          }
        }
        state.phase == BatchPhase.READY -> {
          Button(
            onClick = onStart,
            modifier = Modifier.weight(1f).height(54.dp),
            shape = MaterialTheme.shapes.small,
          ) {
            Text(pluralStringResource(Res.plurals.convert_count, state.jobs.size, state.jobs.size))
          }
        }
        state.phase == BatchPhase.RUNNING -> {
          Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Text(stringResource(Res.string.converting), style = MaterialTheme.typography.titleSmall)
          }
          OutlinedButton(onClick = onCancel, shape = MaterialTheme.shapes.small) {
            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.cancel))
          }
        }
        else -> {
          Button(
            onClick = onExport,
            modifier = Modifier.weight(1f).height(54.dp),
            shape = MaterialTheme.shapes.small,
          ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.save))
          }
          FilledTonalButton(
            onClick = onShare,
            modifier = Modifier.weight(1f).height(54.dp),
            shape = MaterialTheme.shapes.small,
          ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.share))
          }
        }
      }
    }
  }
}
