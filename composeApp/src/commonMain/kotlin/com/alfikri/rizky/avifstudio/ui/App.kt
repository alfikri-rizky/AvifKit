package com.alfikri.rizky.avifstudio.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alfikri.rizky.avifstudio.platform.ExportResult
import com.alfikri.rizky.avifstudio.platform.IncomingFiles
import com.alfikri.rizky.avifstudio.platform.rememberNotificationPermissionRequest
import com.alfikri.rizky.avifstudio.platform.rememberPlatformActions
import com.alfikri.rizky.avifstudio.resources.Res
import com.alfikri.rizky.avifstudio.resources.already_added
import com.alfikri.rizky.avifstudio.resources.app_name
import com.alfikri.rizky.avifstudio.resources.back
import com.alfikri.rizky.avifstudio.resources.export_cancelled
import com.alfikri.rizky.avifstudio.resources.export_failed
import com.alfikri.rizky.avifstudio.resources.nothing_to_export
import com.alfikri.rizky.avifstudio.resources.saved_to
import com.alfikri.rizky.avifstudio.resources.settings
import com.alfikri.rizky.avifstudio.ui.theme.AvifStudioTheme
import org.jetbrains.compose.resources.stringResource

private enum class Screen {
  HOME,
  SETTINGS,
}

@Composable
fun App() {
  val viewModel: StudioViewModel = viewModel { StudioViewModel() }
  val appSettings by viewModel.appSettings.collectAsState()

  AvifStudioTheme(appSettings.themeMode) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      AppContent(viewModel)
    }
  }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AppContent(viewModel: StudioViewModel) {
  val state by viewModel.state.collectAsState()
  val appSettings by viewModel.appSettings.collectAsState()
  val notice by viewModel.notice.collectAsState()
  val incoming by IncomingFiles.pending.collectAsState()

  var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
  val requestNotificationPermission = rememberNotificationPermissionRequest()
  val snackbarHostState = remember { SnackbarHostState() }

  val actions = rememberPlatformActions { result ->
    viewModel.showNotice(
      when (result) {
        is ExportResult.Saved -> Notice.Saved(result.count, result.location)
        ExportResult.Cancelled -> Notice.ExportCancelled
        is ExportResult.Failed -> Notice.ExportFailed(result.message)
      }
    )
  }

  // Files handed to us by "Open with" or "Share to" land here rather than through the picker.
  LaunchedEffect(incoming) {
    if (incoming.isNotEmpty()) {
      viewModel.addSources(IncomingFiles.consume())
      screen = Screen.HOME
    }
  }

  val noticeText = notice?.let { noticeMessage(it) }
  LaunchedEffect(notice, noticeText) {
    val message = noticeText ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(message)
    viewModel.dismissNotice()
  }

  BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

  Scaffold(
    // The app bar lives in the Scaffold slot rather than inside each screen: a TopAppBar applies
    // the status-bar inset itself, so a screen that both consumes the Scaffold's padding and draws
    // its own bar ends up pushed down by that inset twice.
    topBar = {
      TopAppBar(
        title = {
          Text(
            stringResource(
              if (screen == Screen.SETTINGS) Res.string.settings else Res.string.app_name
            )
          )
        },
        navigationIcon = {
          if (screen == Screen.SETTINGS) {
            IconButton(onClick = { screen = Screen.HOME }) {
              Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(Res.string.back),
              )
            }
          }
        },
        actions = {
          if (screen == Screen.HOME) {
            IconButton(onClick = { screen = Screen.SETTINGS }) {
              Icon(Icons.Default.Settings, contentDescription = stringResource(Res.string.settings))
            }
          }
        },
      )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
    containerColor = MaterialTheme.colorScheme.background,
    // Only the horizontal (display cutout) insets are consumed here. The top is already handled by
    // the TopAppBar, and the bottom is handled by each screen, so the bottom bar's surface can run
    // all the way to the edge of the screen with only its content lifted above the nav bar.
    contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
  ) { padding ->
    // Settings slides in from the trailing edge and back out the way it came, so the direction of
    // travel matches the back gesture instead of the screen simply swapping in place.
    AnimatedContent(
      targetState = screen,
      transitionSpec = {
        val forward = targetState == Screen.SETTINGS
        val distance = if (forward) 1 else -1
        (slideInHorizontally(tween(280)) { width -> distance * width / 3 } + fadeIn(tween(220)))
          .togetherWith(
            slideOutHorizontally(tween(280)) { width -> -distance * width / 6 } +
              fadeOut(tween(160))
          )
          .using(SizeTransform(clip = false))
      },
      modifier = Modifier.fillMaxSize(),
      label = "screen",
    ) { current ->
      when (current) {
        Screen.HOME ->
          HomeScreen(
            state = state,
            contentPadding = padding,
            onAddSources = { files ->
              // Asked here rather than on Convert: a short batch can finish while the system
              // dialog is still up, and the completion notification is then dropped.
              requestNotificationPermission()
              viewModel.addSources(files)
            },
            onRemoveSource = viewModel::removeSource,
            onClearAll = viewModel::clearAll,
            onSelectRecipe = viewModel::selectRecipe,
            onUpdateSettings = viewModel::updateSettings,
            onStart = viewModel::start,
            onCancel = viewModel::cancel,
            onRetry = viewModel::retry,
            onShare = { files ->
              if (files.isEmpty()) viewModel.showNotice(Notice.NothingToExport)
              else actions.share(files, "image/*")
            },
            onExport = { files ->
              if (files.isEmpty()) viewModel.showNotice(Notice.NothingToExport)
              else actions.export(files)
            },
          )
        Screen.SETTINGS ->
          SettingsScreen(
            settings = appSettings,
            contentPadding = padding,
            supportsNativeAvifDecoding = actions.supportsNativeAvifDecoding,
            supportsNativeAvifEncoding = actions.supportsNativeAvifEncoding,
            onLanguageChange = viewModel::setLanguage,
            onThemeChange = viewModel::setThemeMode,
          )
      }
    }
  }
}

@Composable
private fun noticeMessage(notice: Notice): String =
  when (notice) {
    Notice.AlreadyAdded -> stringResource(Res.string.already_added)
    Notice.NothingToExport -> stringResource(Res.string.nothing_to_export)
    is Notice.Saved -> stringResource(Res.string.saved_to, notice.count, notice.location)
    Notice.ExportCancelled -> stringResource(Res.string.export_cancelled)
    is Notice.ExportFailed -> stringResource(Res.string.export_failed, notice.message)
  }
