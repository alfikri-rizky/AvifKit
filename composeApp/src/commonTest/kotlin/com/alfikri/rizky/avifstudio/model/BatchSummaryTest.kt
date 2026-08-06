package com.alfikri.rizky.avifstudio.model

import io.github.vinceglb.filekit.PlatformFile
import kotlin.test.Test
import kotlin.test.assertEquals

class BatchSummaryTest {

  @Test
  fun addsUpOnlyTheJobsThatProducedAFile() {
    val jobs =
      listOf(
        done(inputBytes = 1_000_000, outputBytes = 250_000),
        done(inputBytes = 2_000_000, outputBytes = 500_000),
        failed(inputBytes = 4_000_000),
        skipped(inputBytes = 8_000_000),
        pending(inputBytes = 16_000_000),
      )

    val summary = BatchSummary.of(jobs)

    assertEquals(5, summary.total)
    assertEquals(2, summary.succeeded)
    assertEquals(1, summary.failed)
    assertEquals(1, summary.skipped)
    // The failed, skipped and still-pending jobs contribute nothing: counting their input bytes
    // would report space "saved" that was never saved.
    assertEquals(3_000_000, summary.inputBytes)
    assertEquals(750_000, summary.outputBytes)
    assertEquals(2_250_000, summary.savedBytes)
    assertEquals(75, summary.savedPercent)
  }

  @Test
  fun reportsZeroForAnEmptyBatch() {
    val summary = BatchSummary.of(emptyList())
    assertEquals(0, summary.total)
    assertEquals(0, summary.savedBytes)
    assertEquals(0, summary.savedPercent)
  }

  /** A batch that grew is reported as negative savings rather than hidden. */
  @Test
  fun reportsNegativeSavingsWhenOutputGrew() {
    val summary = BatchSummary.of(listOf(done(inputBytes = 100_000, outputBytes = 150_000)))
    assertEquals(-50_000, summary.savedBytes)
    assertEquals(-50, summary.savedPercent)
  }

  private var counter = 0

  private fun source(inputBytes: Long): SourceImage {
    val name = "photo${counter++}.jpg"
    return SourceImage(
      id = name,
      file = PlatformFile("/tmp/$name"),
      displayName = name,
      sizeBytes = inputBytes,
    )
  }

  /**
   * The source is deliberately given a *different* advertised size from what the converter read.
   * Content providers routinely lie about size (or report -1), and the summary has to add up the
   * measured bytes. With both set to the same number this fixture could not tell the two apart, so
   * it passed just as happily when the wrong one was being summed.
   */
  private fun done(inputBytes: Long, outputBytes: Long): ConversionJob {
    val src = source(inputBytes = inputBytes / 2)
    return ConversionJob(
      source = src,
      status =
        JobStatus.Done(
          ConversionOutput(
            file = PlatformFile("/tmp/out-${src.id}"),
            displayName = "out-${src.id}",
            sizeBytes = outputBytes,
            inputBytes = inputBytes,
            inputWidth = 4032,
            inputHeight = 3024,
            width = 1920,
            height = 1080,
            format = OutputFormat.AVIF,
            elapsedMillis = 100,
          )
        ),
    )
  }

  private fun failed(inputBytes: Long) =
    ConversionJob(source(inputBytes), JobStatus.Failed(FailureReason.ENCODE_FAILED))

  private fun skipped(inputBytes: Long) = ConversionJob(source(inputBytes), JobStatus.Skipped)

  private fun pending(inputBytes: Long) = ConversionJob(source(inputBytes), JobStatus.Pending)
}
