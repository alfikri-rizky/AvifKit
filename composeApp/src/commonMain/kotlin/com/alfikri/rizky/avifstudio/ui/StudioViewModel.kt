package com.alfikri.rizky.avifstudio.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alfikri.rizky.avifkit.AvifError
import com.alfikri.rizky.avifkit.PlatformFile
import com.alfikri.rizky.avifstudio.engine.ConversionEngine
import com.alfikri.rizky.avifstudio.engine.ConversionPlanner
import com.alfikri.rizky.avifstudio.model.BatchSummary
import com.alfikri.rizky.avifstudio.model.ConversionJob
import com.alfikri.rizky.avifstudio.model.ConversionSettings
import com.alfikri.rizky.avifstudio.model.FailureReason
import com.alfikri.rizky.avifstudio.model.FileNaming
import com.alfikri.rizky.avifstudio.model.JobStatus
import com.alfikri.rizky.avifstudio.model.Recipe
import com.alfikri.rizky.avifstudio.model.SourceImage
import com.alfikri.rizky.avifstudio.platform.resolveMetadata
import com.alfikri.rizky.avifstudio.settings.AppLanguage
import com.alfikri.rizky.avifstudio.settings.AppSettings
import com.alfikri.rizky.avifstudio.settings.SettingsStore
import com.alfikri.rizky.avifstudio.settings.ThemeMode
import com.alfikri.rizky.avifstudio.settings.applyAppLanguage
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

  val exportableFiles: List<PlatformFile>
    get() = jobs.mapNotNull { it.outputOrNull?.file }
}

class StudioViewModel(
  private val engine: ConversionEngine = ConversionEngine(),
  private val settingsStore: SettingsStore = SettingsStore(),
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

  private var runJob: Job? = null
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
      _state.update { it.copy(recipe = stored.lastRecipe, settings = stored.conversion) }
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
    val existingKeys = _state.value.jobs.mapTo(mutableSetOf()) { it.source.file.identityKey() }
    val additions =
      files
        .filter { it.identityKey() !in existingKeys }
        .map { file ->
          val metadata = file.resolveMetadata()
          ConversionJob(
            source =
              SourceImage(
                id = "src-${nextId++}",
                file = file,
                displayName = metadata.name,
                sizeBytes = metadata.sizeBytes,
              )
          )
        }

    if (additions.isEmpty()) {
      _notice.value = Notice.AlreadyAdded
      return
    }

    _state.update {
      it.copy(
        // Adding to a finished batch starts a fresh one; mixing new Pending rows into old results
        // would make the summary a lie.
        jobs = if (it.phase == BatchPhase.FINISHED) additions else it.jobs + additions,
        phase = BatchPhase.READY,
      )
    }
  }

  fun removeSource(id: String) {
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
        settings = if (recipe == Recipe.CUSTOM) current.settings else recipe.defaultSettings(),
      )
    }
    // Persist the resulting settings too, so the next launch restores the preset *and* whatever
    // it implies rather than a stale set of knobs from a different preset.
    pendingSettingsWrite.value = _state.value.settings
  }

  fun updateSettings(settings: ConversionSettings) {
    _state.update { it.copy(settings = settings) }
    pendingSettingsWrite.value = settings
  }

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
    val sources = snapshot.jobs.map { it.source }
    val names = ConversionPlanner.outputNames(sources, settings.outputFormat)

    _state.update { current ->
      current.copy(
        phase = BatchPhase.RUNNING,
        jobs = current.jobs.map { it.copy(status = JobStatus.Pending) },
      )
    }

    runJob = viewModelScope.launch {
      try {
        sources.forEachIndexed { index, source ->
          updateStatus(source.id, JobStatus.Running)
          updateStatus(source.id, runOne(source, settings, names[index]))
        }
        _state.update { it.copy(phase = BatchPhase.FINISHED) }
      } catch (_: CancellationException) {
        // Anything still queued when the user hit cancel is reported as cancelled rather than
        // left spinning forever.
        _state.update { current ->
          current.copy(
            phase = BatchPhase.FINISHED,
            jobs =
              current.jobs.map { job ->
                if (job.status.isTerminal) job else job.copy(status = JobStatus.Cancelled)
              },
          )
        }
      }
    }
  }

  fun cancel() {
    runJob?.cancel()
  }

  /** Re-runs a single job with the current settings. */
  fun retry(id: String) {
    val current = _state.value
    val job = current.jobs.firstOrNull { it.source.id == id } ?: return
    val settings = current.settings
    val taken =
      current.jobs.mapNotNullTo(mutableSetOf()) {
        if (it.source.id == id) null else it.outputOrNull?.displayName
      }
    val name =
      FileNaming.uniqueName(
        FileNaming.outputName(job.source.displayName, settings.outputFormat),
        taken,
      )

    viewModelScope.launch {
      updateStatus(id, JobStatus.Running)
      updateStatus(id, runOne(job.source, settings, name))
    }
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
      // OutOfMemoryError is an Error, not an Exception — worth catching here precisely because the
      // one-at-a-time gate means the memory is released before the next image starts.
      is OutOfMemoryError -> FailureReason.OUT_OF_MEMORY
      is AvifError.OutOfMemory -> FailureReason.OUT_OF_MEMORY
      is AvifError.UnsupportedFormat,
      is AvifError.InvalidInput -> FailureReason.NOT_AN_IMAGE
      is AvifError.FileError -> FailureReason.UNREADABLE
      is AvifError.EncodingFailed,
      is AvifError.DecodingFailed -> FailureReason.ENCODE_FAILED
      is IllegalArgumentException -> FailureReason.NOT_AN_IMAGE
      is IllegalStateException -> FailureReason.UNREADABLE
      // A revoked or never-granted URI permission — the file is there, we just cannot open it.
      else -> if (isPermissionDenial()) FailureReason.UNREADABLE else FailureReason.UNKNOWN
    }
  return JobStatus.Failed(reason, detail)
}

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
