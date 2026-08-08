package com.alfikri.rizky.avifstudio.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfikri.rizky.avifstudio.model.ConversionSettings
import com.alfikri.rizky.avifstudio.model.Recipe
import com.alfikri.rizky.avifstudio.model.formatBytes
import com.alfikri.rizky.avifstudio.resources.Res
import com.alfikri.rizky.avifstudio.resources.advanced_title
import com.alfikri.rizky.avifstudio.resources.recipe_section_title
import com.alfikri.rizky.avifstudio.resources.target_size
import com.alfikri.rizky.avifstudio.ui.description
import com.alfikri.rizky.avifstudio.ui.title
import org.jetbrains.compose.resources.stringResource

/**
 * The "what do you need?" block: one tap picks a whole encoder configuration.
 *
 * A scrolling row of cards rather than a wrapped grid of chips — seven chips wrapped to four rows
 * and pushed the actual image list off the screen, which is the one thing the user came here to
 * look at.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipePicker(
  selected: Recipe,
  settings: ConversionSettings,
  enabled: Boolean,
  onSelect: (Recipe) -> Unit,
  onUpdateSettings: (ConversionSettings) -> Unit,
  onOpenAdvanced: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
      text = stringResource(Res.string.recipe_section_title),
      style = MaterialTheme.typography.titleMedium,
    )

    // The selected recipe is remembered across launches, so the row has to open on it — otherwise
    // reopening the app on "Convert to WebP" shows a row of cards with the chosen one off-screen.
    // Once, on first composition: re-running it on every selection would yank the row out from
    // under the finger that just tapped a card.
    val rowState = rememberLazyListState()
    val leadIn = with(LocalDensity.current) { 48.dp.roundToPx() }
    LaunchedEffect(Unit) {
      val index = Recipe.displayOrder.indexOf(selected)
      // A negative offset leaves the previous card peeking in, which is what tells the user the
      // row scrolls at all.
      if (index > 0) rowState.scrollToItem(index, -leadIn)
    }

    LazyRow(
      state = rowState,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      // The row bleeds past the screen edge; the fade tells the user that rather than leaving a
      // card looking as if it had been cut off by a bug.
      // Vertical padding is not cosmetic here: the fade renders the row into an offscreen layer,
      // which clips to the row's bounds, so a card's shadow needs room *inside* those bounds or it
      // gets sliced off top and bottom.
      contentPadding = PaddingValues(top = 4.dp, bottom = 10.dp, end = 6.dp),
      modifier = Modifier.fadingEdges(),
    ) {
      items(Recipe.displayOrder, key = { it.name }) { recipe ->
        RecipeCard(
          recipe = recipe,
          selected = recipe == selected,
          enabled = enabled,
          onClick = { onSelect(recipe) },
        )
      }
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = selected.description(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
      )
      TextButton(onClick = onOpenAdvanced, enabled = enabled) {
        Text("${stringResource(Res.string.advanced_title)}  \u203A")
      }
    }

    if (selected.showsSizeTarget) {
      Text(stringResource(Res.string.target_size), style = MaterialTheme.typography.labelLarge)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ConversionSettings.SIZE_TARGET_CHOICES.forEach { bytes ->
          FilterChip(
            selected = settings.targetSizeBytes == bytes,
            enabled = enabled,
            onClick = { onUpdateSettings(settings.copy(targetSizeBytes = bytes)) },
            label = { Text(formatBytes(bytes)) },
            shape = CircleShape,
          )
        }
      }
    }
  }
}

@Composable
private fun RecipeCard(recipe: Recipe, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
  val container by
    animateColorAsState(
      if (selected) MaterialTheme.colorScheme.primaryContainer
      else MaterialTheme.colorScheme.surface,
      tween(200),
      label = "recipeCard",
    )
  // The selected card lifts clear of the others rather than only changing colour.
  val elevation by animateDpAsState(if (selected) 6.dp else 1.dp, tween(200), label = "recipeLift")

  Surface(
    onClick = onClick,
    enabled = enabled,
    // Selection was conveyed by colour, a border and a decorative check icon only — none of which
    // a screen reader announces.
    modifier = Modifier.width(124.dp).height(112.dp).semantics { this.selected = selected },
    shape = MaterialTheme.shapes.medium,
    color = container,
    tonalElevation = if (selected) 3.dp else 0.dp,
    shadowElevation = elevation,
    // The unselected cards are surface-on-surface, so in light mode only the drop shadow gives
    // them an edge — and a drop shadow is invisible against a dark background. An outline is what
    // holds in both themes.
    border =
      if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
      else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Box(Modifier.padding(14.dp)) {
      Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
        Text(recipe.glyph, fontSize = 26.sp)
        Text(
          text = recipe.title(),
          style = MaterialTheme.typography.titleSmall,
          color =
            if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Start,
          maxLines = 2,
        )
      }

      if (selected) {
        Box(Modifier.align(Alignment.TopEnd).size(20.dp), contentAlignment = Alignment.Center) {
          Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
            Icon(
              Icons.Default.Check,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.padding(3.dp).size(14.dp),
            )
          }
        }
      }
    }
  }
}

/**
 * Fades both ends of a horizontally scrolling row. Drawn as an alpha mask rather than a gradient
 * overlay so it works on any background — an overlay painted in the surface colour would show as a
 * pale smear the moment the theme changed.
 */
private fun Modifier.fadingEdges(width: Dp = 24.dp): Modifier =
  this.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
    drawContent()
    val fade = width.toPx()
    drawRect(
      brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), endX = fade),
      blendMode = BlendMode.DstIn,
    )
    drawRect(
      brush =
        Brush.horizontalGradient(
          listOf(Color.Black, Color.Transparent),
          startX = size.width - fade,
          endX = size.width,
        ),
      blendMode = BlendMode.DstIn,
    )
  }
