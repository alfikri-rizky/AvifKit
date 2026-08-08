package com.alfikri.rizky.avifstudio.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alfikri.rizky.avifkit.PlatformFile
import com.alfikri.rizky.avifstudio.engine.ConversionEngine
import com.alfikri.rizky.avifstudio.engine.toImageBitmap
import com.alfikri.rizky.avifstudio.resources.Res
import com.alfikri.rizky.avifstudio.resources.cannot_decode
import com.alfikri.rizky.avifstudio.resources.close
import com.alfikri.rizky.avifstudio.resources.decoding
import com.alfikri.rizky.avifstudio.resources.view_full_screen
import com.alfikri.rizky.avifstudio.resources.viewer_hint
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.stringResource

sealed interface DecodeState {
  data object Loading : DecodeState

  data class Ready(val image: ImageBitmap) : DecodeState

  data class Failed(val message: String) : DecodeState
}

/**
 * Goes through [ConversionEngine] rather than a platform image loader on purpose: the whole point
 * of this app on Android 11 and below is that nothing else on the device can draw an AVIF, so the
 * preview has to come from AvifKit too. The engine's one-at-a-time gate also applies here, which is
 * why a preview opened during a batch waits its turn instead of competing for memory with it.
 */
@Composable
fun rememberDecodedImage(file: PlatformFile, maxDimension: Int): DecodeState {
  var state by remember(file, maxDimension) { mutableStateOf<DecodeState>(DecodeState.Loading) }
  val fallbackMessage = stringResource(Res.string.decoding)

  LaunchedEffect(file, maxDimension) {
    state =
      try {
        DecodeState.Ready(ConversionEngine().decodeForDisplay(file, maxDimension).toImageBitmap())
      } catch (cancellation: CancellationException) {
        // Leaving the screen cancels this effect. Swallowing it rendered
        // "StandaloneCoroutine was cancelled" as if the image were broken.
        throw cancellation
      } catch (error: Throwable) {
        DecodeState.Failed(error.message?.take(120) ?: fallbackMessage)
      }
  }
  return state
}

@Composable
fun ImagePreview(file: PlatformFile, maxDimension: Int, modifier: Modifier = Modifier) {
  val state = rememberDecodedImage(file, maxDimension)
  Box(modifier, contentAlignment = Alignment.Center) {
    when (state) {
      DecodeState.Loading -> CircularProgressIndicator(Modifier.padding(24.dp))
      is DecodeState.Failed ->
        Text(
          text = stringResource(Res.string.cannot_decode, state.message),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(16.dp),
        )
      is DecodeState.Ready ->
        Image(
          bitmap = state.image,
          contentDescription = null,
          contentScale = ContentScale.Fit,
          modifier = Modifier.fillMaxSize(),
        )
    }
  }
}

/** Full-screen viewer with pinch-zoom and double-tap-to-fit. */
@Composable
fun FullScreenImageViewer(file: PlatformFile, onDismiss: () -> Unit) {
  Dialog(
    onDismissRequest = onDismiss,
    properties =
      DialogProperties(
        dismissOnBackPress = true,
        dismissOnClickOutside = false,
        usePlatformDefaultWidth = false,
      ),
  ) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
      // 2560 px is generous enough that zooming still shows real detail, without decoding a 50 MP
      // original into memory just to look at it.
      val state = rememberDecodedImage(file, maxDimension = 2560)
      when (state) {
        DecodeState.Loading ->
          CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        is DecodeState.Failed ->
          Text(
            text = stringResource(Res.string.cannot_decode, state.message),
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(24.dp),
          )
        is DecodeState.Ready ->
          ZoomableImage(state.image, stringResource(Res.string.view_full_screen))
      }

      if (state is DecodeState.Ready) {
        Text(
          text = stringResource(Res.string.viewer_hint),
          color = Color.White.copy(alpha = 0.75f),
          style = MaterialTheme.typography.labelMedium,
          modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
        )
      }

      IconButton(
        onClick = onDismiss,
        modifier =
          Modifier.align(Alignment.TopEnd)
            .padding(16.dp)
            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
      ) {
        Icon(
          Icons.Default.Close,
          contentDescription = stringResource(Res.string.close),
          tint = Color.White,
        )
      }
    }
  }
}

@Composable
private fun ZoomableImage(image: ImageBitmap, contentDescription: String) {
  var scale by remember { mutableFloatStateOf(1f) }
  var offsetX by remember { mutableFloatStateOf(0f) }
  var offsetY by remember { mutableFloatStateOf(0f) }

  Box(
    Modifier.fillMaxSize()
      .pointerInput(Unit) {
        detectTransformGestures { _, pan, zoom, _ ->
          scale = (scale * zoom).coerceIn(1f, 8f)
          if (scale > 1f) {
            offsetX += pan.x
            offsetY += pan.y
          } else {
            // Snapping back to centre when fully zoomed out stops the image drifting off screen.
            offsetX = 0f
            offsetY = 0f
          }
        }
      }
      .pointerInput(Unit) {
        detectTapGestures(
          onDoubleTap = {
            if (scale > 1f) {
              scale = 1f
              offsetX = 0f
              offsetY = 0f
            } else {
              scale = 3f
            }
          }
        )
      }
  ) {
    Image(
      bitmap = image,
      // Without this the whole viewer screen is silent to a screen reader.
      contentDescription = contentDescription,
      contentScale = ContentScale.Fit,
      modifier =
        Modifier.fillMaxSize().graphicsLayer {
          scaleX = scale
          scaleY = scale
          translationX = offsetX
          translationY = offsetY
        },
    )
  }
}
