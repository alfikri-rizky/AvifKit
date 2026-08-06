package com.alfikri.rizky.avifstudio.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.alfikri.rizky.avifstudio.resources.add_more_files
import com.alfikri.rizky.avifstudio.resources.add_more_photos
import com.alfikri.rizky.avifstudio.resources.add_photos
import com.alfikri.rizky.avifstudio.resources.cancel
import com.alfikri.rizky.avifstudio.resources.clear_all
import com.alfikri.rizky.avifstudio.resources.convert_count
import com.alfikri.rizky.avifstudio.resources.converting
import com.alfikri.rizky.avifstudio.resources.count_converted
import com.alfikri.rizky.avifstudio.resources.count_converted_to
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
import com.alfikri.rizky.avifstudio.ui.components.isPhotoPickerAvailable
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

  // Which door the batch came through. "Add more" reopens that one — someone who reached for
  // Files was after something the gallery does not show them (an .avif out of Downloads, say), and
  // sending them back to the gallery hands them a picker their file is missing from.
  var addedFromFiles by rememberSaveable { mutableStateOf(false) }
  // Without a photo picker there is only one door, so every path leads through Files.
  val hasPhotoPicker = isPhotoPickerAvailable()
  val useFilePicker = addedFromFiles || !hasPhotoPicker

  val running = state.phase == BatchPhase.RUNNING

  // What the list renders, held at the last batch that had images in it. Removing the final image
  // empties the list in the same frame the empty state appears, so without this the row and its
  // header blink out first and the fade has nothing left to fade.
  val lastBatch = remember { mutableStateOf(state) }
  if (state.hasSources) lastBatch.value = state

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

    // The body and the footer's scrim share a Box so the scrim is drawn *over* the content. As a
    // sibling in this Column it took a strip of layout height of its own, so it shaded the page
    // background instead of the list — a white band above the footer rather than a shadow.
    Box(Modifier.weight(1f).fillMaxWidth()) {
      // Removing the last image swaps the whole body for a different screen. A hard cut there
      // reads as a glitch, so the two fade through each other.
      AnimatedContent(
        targetState = state.hasSources,
        transitionSpec = { fadeThrough() },
        label = "body",
      ) { hasSources ->
        if (hasSources) {
          JobList(
            state = lastBatch.value,
            onSelectRecipe = onSelectRecipe,
            onUpdateSettings = onUpdateSettings,
            onOpenAdvanced = { showAdvanced = true },
            onClearAll = onClearAll,
            onRemoveSource = onRemoveSource,
            onRetry = onRetry,
            onOpenDetail = { detailJob = it },
            onAddMore = { if (useFilePicker) pickers.pickFiles() else pickers.pickImages() },
            addMoreFromFiles = useFilePicker,
          )
        } else {
          // Centred in the space left over, but scrollable: on a 360x640 dp screen the hero, the
          // headline and the privacy note together are taller than the gap between the app bar and
          // the footer, and the note was being cut in half. fillMaxSize ahead of the scroll keeps
          // the minimum height at one screen, so centring still applies wherever it does fit.
          BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxHeight < 560.dp
            Column(
              modifier =
                Modifier.fillMaxSize()
                  .verticalScroll(rememberScrollState())
                  .padding(horizontal = 20.dp, vertical = 8.dp),
              verticalArrangement = Arrangement.Center,
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              EmptyState(compact = compact)
            }
          }
        }
      }

      FooterScrim(Modifier.align(Alignment.BottomCenter))
    }

    BottomBar(
      state = state,
      hasPhotoPicker = hasPhotoPicker,
      onPickImages = {
        addedFromFiles = false
        pickers.pickImages()
      },
      onPickFiles = {
        addedFromFiles = true
        pickers.pickFiles()
      },
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

/**
 * The old content leaves before the new one arrives, rather than the two dissolving through each
 * other. A plain crossfade double-exposes them — two headings and two buttons on top of each other
 * for the length of the fade, which looks like a rendering fault rather than a transition.
 */
private fun fadeThrough(): ContentTransform =
  fadeIn(tween(210, delayMillis = 90)) togetherWith fadeOut(tween(90))

/** The recipe picker, the batch header and the images themselves — everything that scrolls. */
@Composable
private fun JobList(
  state: StudioUiState,
  onSelectRecipe: (Recipe) -> Unit,
  onUpdateSettings: (ConversionSettings) -> Unit,
  onOpenAdvanced: () -> Unit,
  onClearAll: () -> Unit,
  onRemoveSource: (String) -> Unit,
  onRetry: (String) -> Unit,
  onOpenDetail: (ConversionJob) -> Unit,
  onAddMore: () -> Unit,
  addMoreFromFiles: Boolean,
) {
  val running = state.phase == BatchPhase.RUNNING
  val finished = state.phase == BatchPhase.FINISHED

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    if (finished) {
      item { SummaryCard(state) }
    } else {
      item {
        RecipePicker(
          selected = state.recipe,
          settings = state.settings,
          enabled = !running,
          onSelect = onSelectRecipe,
          onUpdateSettings = onUpdateSettings,
          onOpenAdvanced = onOpenAdvanced,
        )
      }
    }

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

    items(state.jobs, key = { it.source.id }) { job ->
      JobRow(
        job = job,
        showRemove = !running,
        onRemove = { onRemoveSource(job.source.id) },
        onRetry = { onRetry(job.source.id) },
        onOpenDetail = { onOpenDetail(job) },
      )
    }

    if (!running) {
      item {
        OutlinedButton(onClick = onAddMore, shape = MaterialTheme.shapes.small) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          // Says which picker it opens, because it is no longer always the gallery.
          Text(
            stringResource(
              if (addMoreFromFiles) Res.string.add_more_files else Res.string.add_more_photos
            )
          )
        }
      }
    }
  }
}

@Composable
private fun EmptyState(compact: Boolean) {
  Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    HeroMark(compact = compact)
    Spacer(Modifier.height(if (compact) 16.dp else 28.dp))
    Text(
      text = stringResource(Res.string.tagline),
      style = MaterialTheme.typography.headlineSmall,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
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
    Spacer(Modifier.height(if (compact) 16.dp else 24.dp))
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
private fun HeroMark(compact: Boolean) {
  // Roughly 70% on a short screen, which is what keeps the privacy note above the footer on a
  // 360x640 dp device instead of half-cut behind it.
  val scale = if (compact) 0.7f else 1f
  Box(Modifier.size(160.dp * scale), contentAlignment = Alignment.Center) {
    Box(
      Modifier.size(132.dp * scale)
        .clip(RoundedCornerShape(36.dp * scale))
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
      modifier = Modifier.align(Alignment.BottomEnd).size(72.dp * scale),
      shape = RoundedCornerShape(24.dp * scale),
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
        if (summary.succeeded > 0) {
          // Naming the format matters now that a batch can be AVIF, WebP, JPEG or PNG — "3
          // converted" alone leaves the user to remember which recipe they picked.
          val format = summary.singleOutputFormat
          add(
            if (format != null) {
              stringResource(Res.string.count_converted_to, summary.succeeded, format.label)
            } else {
              stringResource(Res.string.count_converted, summary.succeeded)
            }
          )
        }
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

/**
 * The edge the footer casts onto the content behind it.
 *
 * An explicit gradient rather than Surface(shadowElevation): a rectangular elevation shadow against
 * a near-white background renders almost invisibly here, and this has to read the same on both
 * platforms. It only works overlaid on the scrolling content — give it a strip of layout height of
 * its own and it shades the empty page background, which reads as a white band, not a shadow.
 *
 * Black, not onSurface. A scrim must always darken, and onSurface is a *content* token that flips
 * with the theme — in dark mode it is near-white, so this drew a white glow instead of a shadow.
 * Dark mode gets no scrim at all: there the bar is already lighter than the page thanks to the
 * tonal elevation below, which is how Material separates surfaces without shadows.
 */
@Composable
private fun FooterScrim(modifier: Modifier = Modifier) {
  if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
    Box(
      modifier
        .fillMaxWidth()
        .height(10.dp)
        .background(
          Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.07f)))
        )
    )
  }
}

/** Which set of buttons the footer is showing. Named so the fade has something to key on. */
private enum class FooterAction {
  PICK,
  CONVERT,
  CANCEL,
  EXPORT,
}

@Composable
private fun BottomBar(
  state: StudioUiState,
  hasPhotoPicker: Boolean,
  onPickImages: () -> Unit,
  onPickFiles: () -> Unit,
  onStart: () -> Unit,
  onCancel: () -> Unit,
  onShare: () -> Unit,
  onExport: () -> Unit,
) {
  // Keyed on "is there anything to act on?" before phase, so no state can strand the user without
  // a way back to the pickers — an empty FINISHED batch used to show Save/Share only.
  val action =
    when {
      !state.hasSources -> FooterAction.PICK
      state.phase == BatchPhase.READY -> FooterAction.CONVERT
      state.phase == BatchPhase.RUNNING -> FooterAction.CANCEL
      else -> FooterAction.EXPORT
    }

  // Held for the same reason the list holds its batch: clearing the images empties the count in
  // the frame the button starts fading, and "Convert 0 images" is what you would watch fade.
  val lastCount = remember { mutableStateOf(state.jobs.size) }
  if (state.hasSources) lastCount.value = state.jobs.size

  Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
    // The body behind this fades between the list and the empty state; the buttons snapping
    // mid-fade is what gave that away as two animations instead of one.
    AnimatedContent(targetState = action, transitionSpec = { fadeThrough() }, label = "footer") {
      current ->
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        when (current) {
          FooterAction.PICK -> {
            // One button where there is no photo picker: the platform sends both to the same
            // document picker there, and two buttons that open one screen is a false choice.
            if (hasPhotoPicker) {
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
            }
            val filesButton: @Composable () -> Unit = {
              Text("\uD83D\uDCC1", fontSize = 17.sp)
              Spacer(Modifier.width(8.dp))
              Text(stringResource(Res.string.add_files))
            }
            if (hasPhotoPicker) {
              OutlinedButton(
                onClick = onPickFiles,
                modifier = Modifier.weight(1f).height(54.dp),
                shape = MaterialTheme.shapes.small,
              ) {
                filesButton()
              }
            } else {
              Button(
                onClick = onPickFiles,
                modifier = Modifier.weight(1f).height(54.dp),
                shape = MaterialTheme.shapes.small,
              ) {
                filesButton()
              }
            }
          }
          FooterAction.CONVERT -> {
            Button(
              onClick = onStart,
              modifier = Modifier.weight(1f).height(54.dp),
              shape = MaterialTheme.shapes.small,
            ) {
              Text(
                pluralStringResource(Res.plurals.convert_count, lastCount.value, lastCount.value)
              )
            }
          }
          FooterAction.CANCEL -> {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
              Text(
                stringResource(Res.string.converting),
                style = MaterialTheme.typography.titleSmall,
              )
            }
            // Same height as every other footer button, so the bar keeps one height across all
            // four states and the crossfade has no size to resolve.
            OutlinedButton(
              onClick = onCancel,
              modifier = Modifier.height(54.dp),
              shape = MaterialTheme.shapes.small,
            ) {
              Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text(stringResource(Res.string.cancel))
            }
          }
          FooterAction.EXPORT -> {
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
}
