package com.alfikri.rizky.avifstudio.model

import com.alfikri.rizky.avifkit.PlatformFile

/** One image the user picked, before anything has been done to it. */
data class SourceImage(
  /** Stable across recompositions and list reorders; the file path is not (SAF URIs repeat). */
  val id: String,
  val file: PlatformFile,
  val displayName: String,
  val sizeBytes: Long,
)

/** What came out the other end. */
data class ConversionOutput(
  val file: PlatformFile,
  val displayName: String,
  val sizeBytes: Long,
  /**
   * The size of the source as actually read, not as its provider advertised it. A `content://` URI
   * from another app's share sheet can report `-1`, which would make every saving look like a 100%
   * win; this is the number the summary uses.
   */
  val inputBytes: Long,
  /** Source dimensions, read from the header before encoding. Null if the header was unreadable. */
  val inputWidth: Int? = null,
  val inputHeight: Int? = null,
  val width: Int,
  val height: Int,
  val format: OutputFormat,
  val elapsedMillis: Long,
)

/**
 * Why a conversion failed, as a code rather than a sentence, so the UI can translate it.
 *
 * The raw exception text is carried alongside in [JobStatus.Failed.detail] for the cases where a
 * developer or a curious user wants the specifics — but it is never the primary message, because
 * native codec errors are not sentences.
 */
enum class FailureReason {
  /** The file could not be read at all — revoked URI permission, deleted, empty. */
  UNREADABLE,

  /** Read fine, but it is not an image any decoder here understands. */
  NOT_AN_IMAGE,

  /** Decoded fine, but the encoder refused or produced nothing. */
  ENCODE_FAILED,

  /** Ran out of memory — almost always a very large source image. */
  OUT_OF_MEMORY,
  UNKNOWN,
}

/**
 * Where a single job is in its life.
 *
 * [Skipped] exists because "the converted file was not actually smaller, so we kept your original"
 * is a legitimate outcome that is neither success nor failure, and silently returning a bigger file
 * would be the opposite of what this app promises.
 */
sealed interface JobStatus {
  data object Pending : JobStatus

  data object Running : JobStatus

  data class Done(val output: ConversionOutput) : JobStatus

  data class Failed(val reason: FailureReason, val detail: String? = null) : JobStatus

  data object Skipped : JobStatus

  data object Cancelled : JobStatus

  val isTerminal: Boolean
    get() = this !is Pending && this !is Running
}

/** A source paired with its current status. The list of these *is* the batch. */
data class ConversionJob(val source: SourceImage, val status: JobStatus = JobStatus.Pending) {
  val outputOrNull: ConversionOutput?
    get() = (status as? JobStatus.Done)?.output
}

/**
 * Roll-up shown at the top of the results screen.
 *
 * Counts only jobs that actually produced a file, so a batch where half the images failed still
 * reports the truth about the half that worked.
 */
data class BatchSummary(
  val total: Int,
  val succeeded: Int,
  val failed: Int,
  val skipped: Int,
  val inputBytes: Long,
  /** Source dimensions, read from the header before encoding. Null if the header was unreadable. */
  val inputWidth: Int? = null,
  val inputHeight: Int? = null,
  val outputBytes: Long,
) {
  val savedBytes: Long
    get() = inputBytes - outputBytes

  val savedPercent: Int
    get() = savingsPercent(inputBytes, outputBytes)

  companion object {
    fun of(jobs: List<ConversionJob>): BatchSummary {
      var succeeded = 0
      var failed = 0
      var skipped = 0
      var inputBytes = 0L
      var outputBytes = 0L
      for (job in jobs) {
        when (val status = job.status) {
          is JobStatus.Done -> {
            succeeded++
            // Straight off the output: this is the size actually read from the stream. Going via
            // job.source.sizeBytes happened to give the same number, but only because updateStatus
            // back-fills it — a coupling that would break silently.
            inputBytes += status.output.inputBytes
            outputBytes += status.output.sizeBytes
          }
          is JobStatus.Failed -> failed++
          is JobStatus.Skipped -> skipped++
          else -> Unit
        }
      }
      return BatchSummary(
        total = jobs.size,
        succeeded = succeeded,
        failed = failed,
        skipped = skipped,
        inputBytes = inputBytes,
        outputBytes = outputBytes,
      )
    }
  }
}
