package com.alfikri.rizky.avifstudio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfikri.rizky.avifstudio.platform.AVIFKIT_REPOSITORY_URL
import com.alfikri.rizky.avifstudio.platform.openUrl
import com.alfikri.rizky.avifstudio.resources.Res
import com.alfikri.rizky.avifstudio.resources.about
import com.alfikri.rizky.avifstudio.resources.about_body
import com.alfikri.rizky.avifstudio.resources.about_native_avif_none
import com.alfikri.rizky.avifstudio.resources.about_native_avif_read_only
import com.alfikri.rizky.avifstudio.resources.about_native_avif_read_write
import com.alfikri.rizky.avifstudio.resources.about_offline
import com.alfikri.rizky.avifstudio.resources.github_repo
import com.alfikri.rizky.avifstudio.resources.github_repo_desc
import com.alfikri.rizky.avifstudio.resources.language
import com.alfikri.rizky.avifstudio.resources.powered_by
import com.alfikri.rizky.avifstudio.resources.restart_hint
import com.alfikri.rizky.avifstudio.settings.AppLanguage
import com.alfikri.rizky.avifstudio.settings.AppSettings
import com.alfikri.rizky.avifstudio.settings.SaveLocation
import com.alfikri.rizky.avifstudio.settings.ThemeMode
import com.alfikri.rizky.avifstudio.ui.components.DefaultSaveLocationSection
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
  settings: AppSettings,
  contentPadding: PaddingValues,
  supportsNativeAvifDecoding: Boolean,
  supportsNativeAvifEncoding: Boolean,
  onLanguageChange: (AppLanguage) -> Unit,
  onThemeChange: (ThemeMode) -> Unit,
  onDefaultSaveLocationChange: (SaveLocation?) -> Unit,
) {
  Column(
    modifier =
      Modifier.fillMaxSize()
        .padding(contentPadding)
        .verticalScroll(rememberScrollState())
        .navigationBarsPadding()
        .padding(horizontal = 20.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(stringResource(Res.string.language), style = MaterialTheme.typography.titleMedium)
      LanguageDropdown(selected = settings.language, onSelect = onLanguageChange)
      Text(
        text = stringResource(Res.string.restart_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(appearanceTitle(), style = MaterialTheme.typography.titleMedium)
      // Three mutually exclusive options fit on one row; a dropdown here would be a tap worse.
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeMode.entries.forEach { mode ->
          FilterChip(
            selected = settings.themeMode == mode,
            onClick = { onThemeChange(mode) },
            label = { Text("${mode.glyph}  ${mode.label()}") },
          )
        }
      }
    }

    DefaultSaveLocationSection(
      location = settings.defaultSaveLocation,
      onLocationChange = onDefaultSaveLocationChange,
    )

    AboutCard(supportsNativeAvifDecoding, supportsNativeAvifEncoding)
  }
}

/**
 * A dropdown rather than a row of chips: the chip row stops fitting at four or five languages, and
 * this list is meant to grow.
 *
 * Built from a Surface rather than a read-only TextField — a filled text field with an underline
 * reads as "type here", which is the wrong affordance for a picker and the wrong shape next to
 * everything else on this screen.
 */
@Composable
private fun LanguageDropdown(selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
  var expanded by remember { mutableStateOf(false) }
  // DropdownMenu sizes itself to its longest item, which leaves it visibly narrower than the row
  // that opened it. Measuring the anchor and handing that width to the menu keeps the two aligned.
  var anchorWidth by remember { mutableStateOf(0.dp) }
  val density = LocalDensity.current

  Box(Modifier.fillMaxWidth()) {
    Surface(
      onClick = { expanded = true },
      modifier =
        Modifier.fillMaxWidth().onGloballyPositioned { coordinates ->
          anchorWidth = with(density) { coordinates.size.width.toDp() }
        },
      shape = MaterialTheme.shapes.small,
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 1.dp,
      shadowElevation = 2.dp,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
      Row(
        modifier =
          Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(selected.flag, fontSize = 17.sp)
        Spacer(Modifier.width(10.dp))
        Text(
          text = selected.label(),
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.weight(1f),
        )
        Icon(
          Icons.Default.ArrowDropDown,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      shape = MaterialTheme.shapes.small,
      modifier = Modifier.width(anchorWidth),
    ) {
      AppLanguage.entries.forEach { language ->
        DropdownMenuItem(
          text = { Text(language.label(), style = MaterialTheme.typography.bodyMedium) },
          leadingIcon = { Text(language.flag, fontSize = 17.sp) },
          trailingIcon = {
            if (language == selected) {
              Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
            }
          },
          onClick = {
            expanded = false
            onSelect(language)
          },
        )
      }
    }
  }
}

@Composable
private fun AboutCard(supportsNativeAvifDecoding: Boolean, supportsNativeAvifEncoding: Boolean) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 1.dp,
    shadowElevation = 3.dp,
  ) {
    Column(
      Modifier.fillMaxWidth().padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          Icons.Default.Info,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.about), style = MaterialTheme.typography.titleMedium)
      }

      Text(stringResource(Res.string.about_body), style = MaterialTheme.typography.bodyMedium)
      Text(stringResource(Res.string.about_offline), style = MaterialTheme.typography.bodyMedium)

      Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
          text =
            when {
              // Encoding without decoding is not a shape any current OS takes, so it falls in with
              // the "cannot display" copy rather than earning a fourth sentence.
              supportsNativeAvifDecoding && supportsNativeAvifEncoding ->
                stringResource(Res.string.about_native_avif_read_write)
              supportsNativeAvifDecoding -> stringResource(Res.string.about_native_avif_read_only)
              else -> stringResource(Res.string.about_native_avif_none)
            },
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
      }

      OutlinedButton(
        onClick = { openUrl(AVIFKIT_REPOSITORY_URL) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
      ) {
        Column(
          modifier = Modifier.padding(vertical = 6.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text("🔗  ${stringResource(Res.string.github_repo)}")
          Text(
            text = stringResource(Res.string.github_repo_desc),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Text(
        text = stringResource(Res.string.powered_by),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
