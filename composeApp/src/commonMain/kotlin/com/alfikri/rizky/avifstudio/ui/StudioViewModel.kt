package com.alfikri.rizky.avifstudio.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alfikri.rizky.avifkit.AvifError
import com.alfikri.rizky.avifkit.PlatformFile
import com.alfikri.rizky.avifstudio.engine.ConversionEngine
import com.alfikri.rizky.avifstudio.engine.ConversionRunner
import com.alfikri.rizky.avifstudio.model.BatchSummary
import com.alfikri.rizky.avifstudio.model.ConversionJob
import com.alfikri.rizky.avifstudio.model.ConversionSettings
import com.alfikri.rizky.avifstudio.model.FailureReason
import com.alfikri.rizky.avifstudio.model.FileNaming
import com.alfikri.rizky.avifstudio.model.JobStatus
import com.alfikri.rizky.avifstudio.model.Recipe
import com.alfikri.rizky.avifstudio.model.SourceImage
import com.alfikri.rizky.avifstudio.platform.BatchLifecycle
import com.alfikri.rizky.avifstudio.platform.ConversionSession
import com.alfikri.rizky.avifstudio.platform.ResourceSessionCopy
import com.alfikri.rizky.avifstudio.platform.SessionCopy
import com.alfikri.rizky.avifstudio.platform.deviceImageDimensionCap
import com.alfikri.rizky.avifstudio.platform.resolveMetadata
import com.alfikri.rizky.avifstudio.settings.AppLanguage
import com.alfikri.rizky.avifstudio.settings.AppSettings
import com.alfikri.rizky.avifstudio.settings.SettingsRepository
import com.alfikri.rizky.avifstudio.settings.SettingsStore
import com.alfikri.rizky.avifstudio.settings.ThemeMode
import com.alfikri.rizky.avifstudio.settings.applyAppLanguage
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which stage of the flow the screen is in. */
enum class BatchPhase {
  /** Nothing picked, or picked but not started. */
  READY,

  /** A batch is being converted right now. */
  RUNNING,

  /** The batch finished (or was cancelled) and results are on screen. */
  FINISHED,
}

data class StudioUiState(
  val jobs: List<ConversionJob> = emptyList(),
  val recipe: Recipe = Recipe.WEB_READY,
  val settings: ConversionSettings = Recipe.WEB_READY.defaultSettings(),
  val phase: BatchPhase = BatchPhase.READY,
) {
  val summary: BatchSummary
    get() = BatchSummary.of(jobs)

  val hasSources: Boolean
    get() = jobs.isNotEmpty()

  /** 0f..1f across the whole batch, for the progress bar. */
  val progress: Float
    get() = if (jobs.isEmpty()) 0f else completedCount.toFloat() / jobs.size

  val completedCount: Int
    get() = jobs.count { it.status.isTerminal }

  /**
   * Everything Save and Share should hand over — including the sources of jobs that were kept
   * as-is. "3 kept as-is" then quietly writing only the other two is the kind of omission a user
   * discovers much later, in the folder they exported to.
   */
  val exportableFiles: List<PlatformFile>
    get() = jobs.mapNotNull { job ->
      job.outputOrNull?.file ?: job.source.file.takeIf { job.status is JobStatus.Skipped }
    }
}

class StudioViewModel(
  private val engine: ConversionRunner = ConversionEngine(),
  private val settingsStore: SettingsRepository = SettingsStore(),
  private val session: BatchLifecycle = ConversionSession(),
  private val sessionCopy: SessionCopy = ResourceSessionCopy(),
) : ViewModel() {

  private val _state = MutableStateFlow(StudioUiState())
  val state: StateFlow<StudioUiState> = _state.asStateFlow()

  val appSettings: StateFlow<AppSettings> =
    settingsStore.settings.stateIn(
      scope = viewModelScope,
      // Eagerly: the theme and language are read on the very first frame, and a one-frame flash of
      // the wrong theme is exactly the thing a theme setting exists to avoid.
      started = SharingStarted.Eagerly,
      initialValue = AppSettings(),
    )

  /** One-shot user-facing notices (duplicate pick, export outcome). */
  private val _notice = MutableStateFlow<Notice?>(null)
  val notice: StateFlow<Notice?> = _notice.asStateFlow()

  /**
   * Output names already handed out, keyed by source id.
   *
   * A name has to be reserved when it is *assigned*, not when its file appears. A retry runs in its
   * own coroutine alongside the batch and holds no output while it is in flight, so a name derived
   * only from finished outputs handed the same filename to both — and the second encode overwrote
   * the first in the cache directory. Plain mutable state is safe here: every writer runs on the
   * main dispatcher, and the name is chosen before anything suspends.
   */
  private val claimedNames = mutableMapOf<String, String>()

  private var runJob: Job? = null
  private val retryJobs = mutableListOf<Job>()
  private var nextId = 0L

  /**
   * Settings edits waiting to be written. Dragging a quality slider emits on every frame, and
   * writing each of those straight to disk would mean a hundred file writes per gesture.
   */
  private val pendingSettingsWrite = MutableStateFlow<ConversionSettings?>(null)

  init {
    // Restore the last preset and its tweaks once, at startup. Collecting them continuously would
    // fight the user every time they changed something mid-session.
    viewModelScope.launch {
      val stored = settingsStore.settings.first()
      // Untouched presets pick up this device's decode limit; anything the user actually chose is
      // restored exactly as they left it, "Original size" included.
      val restored =
        if (stored.conversion == stored.lastRecipe.defaultSettings()) {
          stored.lastRecipe.deviceDefaults()
        } else {
          stored.conversion
        }
      _state.update { it.copy(recipe = stored.lastRecipe, settings = restored) }
    }
    viewModelScope.launch {
      // collectLatest + delay is debounce without the experimental annotation: each new value
      // cancels the pending write, so only the value that survives the pause reaches disk.
      pendingSettingsWrite.filterNotNull().collectLatest { settings ->
        delay(SETTINGS_WRITE_DELAY_MS)
        settingsStore.setConversionSettings(settings)
      }
    }
  }

  fun setLanguage(language: AppLanguage) {
    // Apply before persisting so the UI turns over immediately; the write is only for next launch.
    applyAppLanguage(language.tag)
    viewModelScope.launch { settingsStore.setLanguage(language) }
  }

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch { settingsStore.setThemeMode(mode) }
  }

  /**
   * Adds picked files, ignoring ones already in the list.
   *
   * Re-picking the same photo is the most common way a user "adds more", and converting it twice
   * would produce a duplicate output and double the wait.
   */
  fun addSources(files: List<PlatformFile>) {
    if (files.isEmpty()) return
    // Adding to a finished batch starts a fresh one, so nothing carries over from it — not the
    // already-added check, not the names in use. Re-picking a photo from the batch just finished is
    // an ordinary way to start again, and refusing it as a duplicate left the user tapping Add
    // photos with nothing happening but an "already added" message.
    val carried = if (_state.value.phase == BatchPhase.FINISHED) emptyList() else _state.value.jobs
    val existingKeys = carried.mapTo(mutableSetOf()) { it.source.file.identityKey() }
    // Display names are routinely not unique. The Android photo picker redacts the real filename
    // and hands back one synthesised from the file's date, so an album imported in one go comes
    // back as twenty rows all called IMG_20260806_135818.jpg — the user cannot tell which row is
    // which, or which of the exported files came from where. Two folders each holding IMG_0042.jpg
    // do the same thing. Disambiguate once here so the list, the results and the saved files all
    // agree on a name.
    val usedNames = carried.mapTo(mutableSetOf()) { it.source.displayName }
    val additions =
      files
        .filter { it.identityKey() !in existingKeys }
        .map { file ->
          val metadata = file.resolveMetadata()
          val displayName = FileNaming.uniqueName(metadata.name, usedNames)
          usedNames += displayName
          ConversionJob(
            source =
              SourceImage(
                id = "src-${nextId++}",
                file = file,
                displayName = displayName,
                sizeBytes = metadata.sizeBytes,
              )
          )
        }

    if (additions.isEmpty()) {
      _notice.value = Notice.AlreadyAdded
      return
    }

    _state.update { current ->
      current.copy(
        // Adding to a finished batch starts a fresh one; mixing new Pending rows into old results
        // would make the summary a lie.
        jobs = if (current.phase == BatchPhase.FINISHED) additions else current.jobs + additions,
        // A share can arrive at any moment — including mid-batch, since this is a share target.
        // Resetting to READY there would hide the progress bar, re-enable Clear all and Remove,
        // and strand the new rows on Pending forever, because the run loop had already snapshotted
        // its work. Leave RUNNING alone; the loop below picks the new rows up.
        phase = if (current.phase == BatchPhase.RUNNING) BatchPhase.RUNNING else BatchPhase.READY,
      )
    }
  }

  fun removeSource(id: String) {
    claimedNames.remove(id)
    _state.update { current ->
      val remaining = current.jobs.filterNot { it.source.id == id }
      current.copy(
        jobs = remaining,
        phase = if (remaining.isEmpty()) BatchPhase.READY else current.phase,
      )
    }
  }

  fun clearAll() {
    runJob?.cancel()
    runJob = null
    retryJobs.forEach { it.cancel() }
    retryJobs.clear()
    claimedNames.clear()
    val keep = _state.value
    _state.value = StudioUiState(recipe = keep.recipe, settings = keep.settings)
    viewModelScope.launch { runCatching { engine.clearOutputs() } }
  }

  fun selectRecipe(recipe: Recipe) {
    viewModelScope.launch { settingsStore.setLastRecipe(recipe) }
    _state.update { current ->
      current.copy(
        recipe = recipe,
        // Custom keeps whatever the user had — that is the point of Custom.
        settings = if (recipe == Recipe.CUSTOM) current.settings else recipe.deviceDefaults(),
      )
    }
    // Persist the resulting settings too, so the next launch restores the preset *and* whatever
    // it implies rather than a stale set of knobs from a different preset.
    pendingSettingsWrite.value = _state.value.settings
  }

  fun updateSettings(settings: ConversionSettings) {
    _state.update { current ->
      current.copy(
        settings = settings,
        // Once a knob is off the preset's own defaults, the preset is no longer what is being
        // applied. Leaving "Web-ready" selected would both misdescribe the encode and let one
        // tap on that still-selected card silently discard the edit.
        recipe = if (settings == current.recipe.deviceDefaults()) current.recipe else Recipe.CUSTOM,
      )
    }
    pendingSettingsWrite.value = settings
  }

  /**
   * A preset's defaults with this device's decode limit filled in. Every path that turns a recipe
   * into settings goes through here, including the equality check above — comparing against the
   * uncapped defaults would flip the preset to Custom on exactly the devices that need the cap.
   */
  private fun Recipe.deviceDefaults(): ConversionSettings =
    defaultSettings().withDeviceLimit(deviceImageDimensionCap())

  fun showNotice(notice: Notice) {
    _notice.value = notice
  }

  fun dismissNotice() {
    _notice.value = null
  }

  /** Runs the whole batch. A second call while one is running is ignored. */
  fun start() {
    if (runJob?.isActive == true) return
    val snapshot = _state.value
    if (snapshot.jobs.isEmpty()) return

    val settings = snapshot.settings

    _state.update { current ->
      current.copy(
        phase = BatchPhase.RUNNING,
        jobs = current.jobs.map { it.copy(status = JobStatus.Pending) },
      )
    }

    val job = viewModelScope.launch {
      startSession()
      try {
        // Drains the live list rather than a snapshot, so anything shared into the app while
        // this is running still gets converted instead of sitting on Pending forever.
        while (true) {
          val next =
            _state.value.jobs.firstOrNull { it.status == JobStatus.Pending }?.source ?: break
          updateStatus(next.id, JobStatus.Running)
          updateStatus(next.id, runOne(next, settings, outputNameFor(next, settings)))
          updateSession()
        }
        _state.update { it.copy(phase = BatchPhase.FINISHED) }
        finishSession(cancelled = false)
      } catch (_: CancellationException) {
        // Anything still queued when the user hit cancel is reported as cancelled rather than
        // left spinning forever.
        //
        // Only the run that is still current may write this. clearAll() cancels the job and
        // then empties the state; without this guard the cancellation handler lands afterwards
        // and revives an empty batch into FINISHED, which has no way back to the pickers.
        if (runJob !== coroutineContext[Job]) {
          // clearAll() already emptied the batch. Nothing to report on work the user just wiped —
          // tear the ongoing notification down without posting a replacement.
          runCatching { session.finish(null) }
          return@launch
        }
        _state.update { current ->
          current.copy(
            phase = BatchPhase.FINISHED,
            jobs =
              current.jobs.map { job ->
                if (job.status.isTerminal) job else job.copy(status = JobStatus.Cancelled)
              },
          )
        }
        finishSession(cancelled = true)
      }
    }
    runJob = job
  }

  fun cancel() {
    runJob?.cancel()
    retryJobs.forEach { it.cancel() }
    retryJobs.clear()
  }

  /** Re-runs a single job with the current settings. */
  fun retry(id: String) {
    val current = _state.value
    val job = current.jobs.firstOrNull { it.source.id == id } ?: return
    val settings = current.settings
    val name = outputNameFor(job.source, settings)

    // Tracked so Cancel and Clear all stop a retry too. Left untracked, it kept the single
    // encode permit while clearAll() deleted the directory underneath it.
    lateinit var retry: Job
    retry = viewModelScope.launch {
      try {
        updateStatus(id, JobStatus.Running)
        updateStatus(id, runOne(job.source, settings, name))
      } finally {
        retryJobs.remove(retry)
      }
    }
    retryJobs.add(retry)
  }

  /**
   * Starts the platform's keep-alive as the batch starts, and stops it when the batch ends — driven
   * from here rather than from composition, because a batch and a composition have different
   * lifetimes. Swiping the app away used to leave the Android service running with an undismissable
   * notification, and a batch that finished while the app was backgrounded never posted its "done"
   * notification at all, because recomposition was paused.
   *
   * Every call is guarded: a notification is worth strictly less than the conversion it describes,
   * so nothing here is allowed to abort a batch.
   */
  private suspend fun startSession() {
    runCatching {
      session.start(
        text = sessionCopy.running(0, _state.value.jobs.size),
        channel = sessionCopy.channel(),
      )
    }
  }

  private suspend fun updateSession() {
    val current = _state.value
    runCatching {
      session.update(
        completed = current.completedCount,
        total = current.jobs.size,
        text = sessionCopy.running(current.completedCount, current.jobs.size),
      )
    }
  }

  /**
   * Announces the result, unless there is nothing worth announcing. A cancelled batch says it
   * stopped rather than claiming success, and a batch where nothing succeeded posts nothing at all
   * — the failures are already on the screen the user is looking at.
   */
  private suspend fun finishSession(cancelled: Boolean) {
    val current = _state.value
    val summary = current.summary
    runCatching {
      val completion =
        when {
          cancelled -> sessionCopy.cancelled(current.completedCount, current.jobs.size)
          summary.succeeded == 0 -> null
          else -> sessionCopy.finished(summary.succeeded, summary.savedBytes)
        }
      session.finish(completion)
    }
  }

  /**
   * A unique output name for [source], measured against the names every other job has already
   * claimed. Computed per item rather than for the batch up front, because the queue can grow
   * mid-run when something is shared into the app.
   */
  private fun outputNameFor(source: SourceImage, settings: ConversionSettings): String {
    val taken =
      _state.value.jobs.mapNotNullTo(mutableSetOf()) {
        if (it.source.id == source.id) null else it.outputOrNull?.displayName
      }
    claimedNames.forEach { (id, name) -> if (id != source.id) taken += name }
    val name =
      FileNaming.uniqueName(FileNaming.outputName(source.displayName, settings.outputFormat), taken)
    claimedNames[source.id] = name
    return name
  }

  private suspend fun runOne(
    source: SourceImage,
    settings: ConversionSettings,
    outputName: String,
  ): JobStatus =
    try {
      engine.convert(source, settings, outputName)?.let { JobStatus.Done(it) } ?: JobStatus.Skipped
    } catch (cancellation: CancellationException) {
      throw cancellation
    } catch (error: Throwable) {
      error.toFailure()
    }

  private fun updateStatus(id: String, status: JobStatus) {
    _state.update { current ->
      current.copy(
        jobs =
          current.jobs.map { job ->
            if (job.source.id != id) return@map job
            // A finished job also corrects its own source size: what the provider advertised
            // before the file was opened is a guess, what the converter read is a fact.
            val measured = (status as? JobStatus.Done)?.output?.inputBytes
            job.copy(
              source = if (measured != null) job.source.copy(sizeBytes = measured) else job.source,
              status = status,
            )
          }
      )
    }
  }

  override fun onCleared() {
    runJob?.cancel()
    retryJobs.forEach { it.cancel() }
    super.onCleared()
  }

  private companion object {
    /** Long enough to swallow a slider drag, short enough that leaving the sheet still saves. */
    const val SETTINGS_WRITE_DELAY_MS = 400L
  }
}

/** Transient messages shown in a snackbar. */
sealed interface Notice {
  data object AlreadyAdded : Notice

  data object NothingToExport : Notice

  data class Saved(val count: Int, val location: String) : Notice

  data object ExportCancelled : Notice

  data class ExportFailed(val message: String) : Notice
}

/**
 * Identity for de-duplicating picks. Content URIs and file URLs are both stable strings for the
 * same underlying file within one picking session, which is all this needs to be.
 */
private fun PlatformFile.identityKey(): String = toString()

/**
 * Classifies whatever the codec threw into something the UI can translate, keeping the raw text as
 * a secondary detail. Native encoder failures are not sentences, so they are never the headline.
 */
private fun Throwable.toFailure(): JobStatus.Failed {
  val detail = message?.trim()?.takeIf { it.isNotEmpty() }?.take(160)
  val reason =
    when (this) {
      is AvifError.OutOfMemory -> FailureReason.OUT_OF_MEMORY
      is AvifError.UnsupportedFormat,
      is AvifError.InvalidInput -> FailureReason.NOT_AN_IMAGE
      is AvifError.FileError -> FailureReason.UNREADABLE
      is AvifError.EncodingFailed,
      is AvifError.DecodingFailed -> FailureReason.ENCODE_FAILED
      is IllegalArgumentException -> FailureReason.NOT_AN_IMAGE
      is IllegalStateException -> FailureReason.UNREADABLE
      else ->
        when {
          isOutOfMemory() -> FailureReason.OUT_OF_MEMORY
          // A revoked or never-granted URI permission — the file is there, we just cannot open it.
          isPermissionDenial() -> FailureReason.UNREADABLE
          else -> FailureReason.UNKNOWN
        }
    }
  return JobStatus.Failed(reason, detail)
}

/**
 * `OutOfMemoryError` exists on both platforms but not in the common stdlib, so it is matched by
 * name like the check below. Worth classifying at all because the one-at-a-time gate means the
 * memory is released before the next image starts, so the batch can carry on.
 */
private fun Throwable.isOutOfMemory(): Boolean = this::class.simpleName == "OutOfMemoryError"

/**
 * Security failures arrive as platform-specific types (SecurityException on Android, an NSError
 * wrapper on iOS), so they are recognised by name rather than by class to keep this in common code.
 */
private fun Throwable.isPermissionDenial(): Boolean {
  val name = this::class.simpleName.orEmpty()
  return name.contains("Security", ignoreCase = true) ||
    message?.contains("no access", ignoreCase = true) == true ||
    message?.contains("permission", ignoreCase = true) == true
}
