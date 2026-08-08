package com.alfikri.rizky.avifstudio.ui

import com.alfikri.rizky.avifstudio.engine.ConversionRunner
import com.alfikri.rizky.avifstudio.model.ConversionOutput
import com.alfikri.rizky.avifstudio.model.ConversionSettings
import com.alfikri.rizky.avifstudio.model.JobStatus
import com.alfikri.rizky.avifstudio.model.OutputFormat
import com.alfikri.rizky.avifstudio.model.Recipe
import com.alfikri.rizky.avifstudio.model.SourceImage
import com.alfikri.rizky.avifstudio.platform.BatchLifecycle
import com.alfikri.rizky.avifstudio.platform.SessionChannel
import com.alfikri.rizky.avifstudio.platform.SessionCopy
import com.alfikri.rizky.avifstudio.platform.SessionText
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
import kotlin.test.assertNotNull
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
      val viewModel = StudioViewModel(engine, FakeSettings(), FakeSession(), FakeCopy)
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
      val viewModel = StudioViewModel(engine, FakeSettings(), FakeSession(), FakeCopy)
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
      val viewModel = StudioViewModel(engine, FakeSettings(), FakeSession(), FakeCopy)
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
      val viewModel = StudioViewModel(engine, FakeSettings(), FakeSession(), FakeCopy)
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
      val viewModel = StudioViewModel(FakeRunner(), FakeSettings(), FakeSession(), FakeCopy)
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
      val viewModel = StudioViewModel(FakeRunner(), FakeSettings(), FakeSession(), FakeCopy)
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
      val viewModel = StudioViewModel(engine, FakeSettings(), FakeSession(), FakeCopy)
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

  /**
   * The keep-alive used to live in composition. On Android that meant the foreground service
   * outlived a composition destroyed by a swipe-away; on iOS it meant a batch that finished while
   * the app was backgrounded never posted its notification, because recomposition was paused. This
   * runs a whole batch with no composition in existence at all.
   */
  @Test
  fun runsTheKeepAliveFromTheBatchRatherThanFromComposition() =
    runTest(dispatcher) {
      val engine = FakeRunner()
      val session = FakeSession()
      val viewModel = StudioViewModel(engine, FakeSettings(), session, FakeCopy)
      viewModel.addSources(files("a.jpg", "b.jpg"))

      viewModel.start()
      testScheduler.advanceUntilIdle()
      assertEquals(1, session.starts, "the batch never claimed the platform keep-alive")

      engine.completeAll()
      testScheduler.advanceUntilIdle()

      assertNotNull(
        session.finishes.single(),
        "a batch that succeeded must announce itself even with nothing on screen",
      )
      assertTrue(session.updates > 0, "progress was never reported")
    }

  /** "Conversion finished" for a batch the user stopped is a lie the notification shade keeps. */
  @Test
  fun cancellingDoesNotAnnounceSuccess() =
    runTest(dispatcher) {
      val engine = FakeRunner()
      val session = FakeSession()
      val viewModel = StudioViewModel(engine, FakeSettings(), session, FakeCopy)
      viewModel.addSources(files("a.jpg", "b.jpg"))
      viewModel.start()
      testScheduler.advanceUntilIdle()

      viewModel.cancel()
      testScheduler.advanceUntilIdle()

      val completion = assertNotNull(session.finishes.single(), "the user is not told it stopped")
      assertEquals("cancelled", completion.title)
    }

  /** Clearing wipes the batch, so there is nothing left to report on. */
  @Test
  fun clearingTearsTheKeepAliveDownSilently() =
    runTest(dispatcher) {
      val engine = FakeRunner()
      val session = FakeSession()
      val viewModel = StudioViewModel(engine, FakeSettings(), session, FakeCopy)
      viewModel.addSources(files("a.jpg"))
      viewModel.start()
      testScheduler.advanceUntilIdle()

      viewModel.clearAll()
      testScheduler.advanceUntilIdle()

      assertEquals(listOf<SessionText?>(null), session.finishes)
    }

  /**
   * Two photos can share a filename — `IMG_0001.jpg` out of two different folders is the everyday
   * case. The batch handles that, because it names each job after the outputs already written. A
   * retry does not run in the batch: it has its own coroutine and holds no output while in flight,
   * so two retries started together both looked at an empty set of written names and claimed the
   * same one. Whichever finished second overwrote the other in the cache.
   */
  @Test
  fun twoRetriesOfIdenticallyNamedPhotosGetDifferentOutputNames() =
    runTest(dispatcher) {
      val engine = RecordingRunner(failEverything = true)
      val viewModel = StudioViewModel(engine, FakeSettings(), FakeSession(), FakeCopy)
      viewModel.addSources(listOf(PlatformFile("/holiday/a.jpg"), PlatformFile("/work/a.jpg")))
      viewModel.start()
      engine.completeAll()
      testScheduler.advanceUntilIdle()

      engine.reset(failEverything = false)
      val ids = viewModel.state.value.jobs.map { it.source.id }
      // Both in flight before either can finish — the situation the batch loop never creates.
      ids.forEach(viewModel::retry)
      testScheduler.advanceUntilIdle()

      val retried = engine.requestedNames.takeLast(2)
      assertEquals(2, retried.toSet().size, "both retries claimed the same output file: $retried")
    }

  /**
   * The Android photo picker redacts real filenames and synthesises one from the file's date, so a
   * gallery imported in a single go comes back as N rows all called `IMG_20260806_135818.jpg`.
   * Verified on an emulator: seven picked photos, four identical labels. Two folders each holding
   * `IMG_0042.jpg` produce the same thing.
   */
  @Test
  fun givesIdenticallyNamedPhotosDistinctLabels() =
    runTest(dispatcher) {
      val viewModel = StudioViewModel(FakeRunner(), FakeSettings(), FakeSession(), FakeCopy)
      viewModel.addSources(
        listOf(
          PlatformFile("/one/IMG_0042.jpg"),
          PlatformFile("/two/IMG_0042.jpg"),
          PlatformFile("/three/IMG_0042.jpg"),
        )
      )
      testScheduler.advanceUntilIdle()

      val names = viewModel.state.value.jobs.map { it.source.displayName }
      assertEquals(
        3,
        names.toSet().size,
        "the picked list shows the same label three times: $names",
      )
    }

  /** Re-picking a photo from the batch that just finished is how people start a second run. */
  @Test
  fun acceptsAPhotoAgainOnceTheBatchIsFinished() =
    runTest(dispatcher) {
      val engine = FakeRunner()
      val viewModel = StudioViewModel(engine, FakeSettings(), FakeSession(), FakeCopy)
      viewModel.addSources(files("a.jpg"))
      viewModel.start()
      engine.completeAll()
      testScheduler.advanceUntilIdle()
      assertEquals(BatchPhase.FINISHED, viewModel.state.value.phase)

      viewModel.addSources(files("a.jpg"))
      testScheduler.advanceUntilIdle()

      assertEquals(1, viewModel.state.value.jobs.size)
      assertEquals(
        BatchPhase.READY,
        viewModel.state.value.phase,
        "re-picking a photo from the finished batch was refused as a duplicate",
      )
    }

  private fun files(vararg names: String) = names.map { PlatformFile("/tmp/$it") }

  /** Records the output name it was asked for, so naming can be asserted without a filesystem. */
  private class RecordingRunner(private var failEverything: Boolean) : ConversionRunner {
    private var gate = CompletableDeferred<Unit>()
    val requestedNames = mutableListOf<String>()

    fun completeAll() {
      if (!gate.isCompleted) gate.complete(Unit)
    }

    fun reset(failEverything: Boolean) {
      this.failEverything = failEverything
      gate = CompletableDeferred<Unit>().also { it.complete(Unit) }
    }

    override suspend fun convert(
      source: SourceImage,
      settings: ConversionSettings,
      outputName: String,
    ): ConversionOutput? {
      requestedNames += outputName
      gate.await()
      if (failEverything) throw IllegalStateException("boom")
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

    override suspend fun clearOutputs() = Unit
  }

  /** Real copy needs a platform behind it; these tests are about who calls what, not wording. */
  private object FakeCopy : SessionCopy {
    override suspend fun channel() = SessionChannel("channel", "description")

    override suspend fun running(completed: Int, total: Int) =
      SessionText("running", "$completed/$total")

    override suspend fun finished(succeeded: Int, savedBytes: Long) =
      SessionText("finished", "$succeeded")

    override suspend fun cancelled(completed: Int, total: Int) =
      SessionText("cancelled", "$completed/$total")
  }

  private class FakeSession : BatchLifecycle {
    var starts = 0
      private set

    var updates = 0
      private set

    val finishes = mutableListOf<SessionText?>()

    override fun start(text: SessionText, channel: SessionChannel) {
      starts++
    }

    override fun update(completed: Int, total: Int, text: SessionText) {
      updates++
    }

    override fun finish(completion: SessionText?) {
      finishes += completion
    }
  }

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
