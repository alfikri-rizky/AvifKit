package com.alfikri.rizky.avifstudio.ui

import com.alfikri.rizky.avifstudio.engine.ConversionRunner
import com.alfikri.rizky.avifstudio.model.ConversionOutput
import com.alfikri.rizky.avifstudio.model.ConversionSettings
import com.alfikri.rizky.avifstudio.model.JobStatus
import com.alfikri.rizky.avifstudio.model.OutputFormat
import com.alfikri.rizky.avifstudio.model.Recipe
import com.alfikri.rizky.avifstudio.model.SourceImage
import com.alfikri.rizky.avifstudio.settings.AppLanguage
import com.alfikri.rizky.avifstudio.settings.AppSettings
import com.alfikri.rizky.avifstudio.settings.SettingsRepository
import com.alfikri.rizky.avifstudio.settings.ThemeMode
import io.github.vinceglb.filekit.PlatformFile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * The batch state machine, driven without a codec or a filesystem.
 *
 * These exist because the two worst bugs in this screen were not about pixels: a share arriving
 * mid-run stranded rows on Pending and hid the progress UI, and clearing during a run left the app
 * in a state with no way back to the pickers. Neither was reachable by any test that did not
 * construct the ViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StudioViewModelTest {

  private val dispatcher = StandardTestDispatcher()

  @BeforeTest
  fun setUp() {
    // viewModelScope is hard-wired to Dispatchers.Main.
    Dispatchers.setMain(dispatcher)
  }

  @AfterTest
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun convertsEveryPickedImage() =
    runTest(dispatcher) {
      val engine = FakeRunner()
      val viewModel = StudioViewModel(engine, FakeSettings())
      viewModel.addSources(files("a.jpg", "b.jpg"))

      viewModel.start()
      engine.completeAll()
      testScheduler.advanceUntilIdle()

      assertEquals(BatchPhase.FINISHED, viewModel.state.value.phase)
      assertEquals(2, viewModel.state.value.summary.succeeded)
    }

  /**
   * The app is a share target, so a share can land at any moment — including mid-batch. It used to
   * reset the phase to READY (hiding the progress bar and re-enabling Clear all) and strand the new
   * row on Pending forever, because the run loop had snapshotted its work before launching.
   */
  @Test
  fun absorbsAnImageSharedInWhileTheBatchIsRunning() =
    runTest(dispatcher) {
      val engine = FakeRunner()
      val viewModel = StudioViewModel(engine, FakeSettings())
      viewModel.addSources(files("a.jpg", "b.jpg"))
      viewModel.start()
      testScheduler.advanceUntilIdle()

      viewModel.addSources(files("shared.jpg"))
      testScheduler.advanceUntilIdle()

      assertEquals(BatchPhase.RUNNING, viewModel.state.value.phase)

      engine.completeAll()
      testScheduler.advanceUntilIdle()

      assertEquals(3, viewModel.state.value.summary.succeeded)
      assertTrue(
        viewModel.state.value.jobs.none { it.status == JobStatus.Pending },
        "a row shared in mid-run was left stranded on Pending",
      )
    }

  /**
   * `clearAll` cancels the run and empties the state; the cancellation handler then landed
   * afterwards and revived the empty batch into FINISHED, which showed Save/Share and offered no
   * way to add images.
   */
  @Test
  fun clearingDuringARunLeavesTheAppAbleToStartOver() =
    runTest(dispatcher) {
      val engine = FakeRunner()
      val viewModel = StudioViewModel(engine, FakeSettings())
      viewModel.addSources(files("a.jpg", "b.jpg"))
      viewModel.start()
      testScheduler.advanceUntilIdle()

      viewModel.clearAll()
      testScheduler.advanceUntilIdle()

      val state = viewModel.state.value
      assertFalse(state.hasSources)
      assertEquals(
        BatchPhase.READY,
        state.phase,
        "an empty batch left in FINISHED strands the user with Save/Share and no pickers",
      )
    }

  @Test
  fun cancellingMarksEverythingStillQueuedAsCancelled() =
    runTest(dispatcher) {
      val engine = FakeRunner()
      val viewModel = StudioViewModel(engine, FakeSettings())
      viewModel.addSources(files("a.jpg", "b.jpg", "c.jpg"))
      viewModel.start()
      testScheduler.advanceUntilIdle()

      viewModel.cancel()
      testScheduler.advanceUntilIdle()

      val state = viewModel.state.value
      assertEquals(BatchPhase.FINISHED, state.phase)
      assertTrue(
        state.jobs.all { it.status.isTerminal },
        "cancelling left a row spinning: ${state.jobs.map { it.status }}",
      )
    }

  @Test
  fun refusesToAddTheSameFileTwice() =
    runTest(dispatcher) {
      val viewModel = StudioViewModel(FakeRunner(), FakeSettings())
      viewModel.addSources(files("a.jpg"))
      viewModel.addSources(files("a.jpg"))
      testScheduler.advanceUntilIdle()

      assertEquals(1, viewModel.state.value.jobs.size)
      assertEquals(Notice.AlreadyAdded, viewModel.notice.value)
    }

  /** Editing a knob means the named preset no longer describes what will be encoded. */
  @Test
  fun editingSettingsSwitchesTheSelectionToCustom() =
    runTest(dispatcher) {
      val viewModel = StudioViewModel(FakeRunner(), FakeSettings())
      viewModel.selectRecipe(Recipe.WEB_READY)
      testScheduler.advanceUntilIdle()

      viewModel.updateSettings(viewModel.state.value.settings.copy(quality = 30))
      testScheduler.advanceUntilIdle()

      assertEquals(Recipe.CUSTOM, viewModel.state.value.recipe)
      assertEquals(30, viewModel.state.value.settings.quality)
    }

  @Test
  fun keepingAnOriginalStillOffersItForExport() =
    runTest(dispatcher) {
      val engine = FakeRunner(keepOriginals = true)
      val viewModel = StudioViewModel(engine, FakeSettings())
      viewModel.addSources(files("a.jpg"))
      viewModel.start()
      engine.completeAll()
      testScheduler.advanceUntilIdle()

      assertEquals(1, viewModel.state.value.summary.skipped)
      assertEquals(
        1,
        viewModel.state.value.exportableFiles.size,
        "a kept-as-is image must still be written when the user taps Save",
      )
    }

  private fun files(vararg names: String) = names.map { PlatformFile("/tmp/$it") }

  /** Converts on demand: [completeAll] releases whatever the ViewModel is waiting on. */
  private class FakeRunner(private val keepOriginals: Boolean = false) : ConversionRunner {
    private var gate = CompletableDeferred<Unit>()
    var cleared = 0
      private set

    fun completeAll() {
      if (!gate.isCompleted) gate.complete(Unit)
    }

    override suspend fun convert(
      source: SourceImage,
      settings: ConversionSettings,
      outputName: String,
    ): ConversionOutput? {
      gate.await()
      if (keepOriginals) return null
      return ConversionOutput(
        file = PlatformFile("/tmp/out/$outputName"),
        displayName = outputName,
        sizeBytes = 100,
        inputBytes = 1000,
        width = 100,
        height = 100,
        format = OutputFormat.AVIF,
        elapsedMillis = 1,
      )
    }

    override suspend fun clearOutputs() {
      cleared++
      gate = CompletableDeferred()
    }
  }

  private class FakeSettings : SettingsRepository {
    override val settings: Flow<AppSettings> = MutableStateFlow(AppSettings())

    override suspend fun setLanguage(language: AppLanguage) = Unit

    override suspend fun setThemeMode(mode: ThemeMode) = Unit

    override suspend fun setLastRecipe(recipe: Recipe) = Unit

    override suspend fun setConversionSettings(settings: ConversionSettings) = Unit
  }
}
