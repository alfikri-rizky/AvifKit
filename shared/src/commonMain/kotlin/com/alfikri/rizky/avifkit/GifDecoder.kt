package com.alfikri.rizky.avifkit

/**
 * Streaming GIF87a/GIF89a decoder shared by both platforms.
 *
 * It exists because neither platform offers a usable animated-GIF frame reader across the versions
 * AvifKit supports: Android's `ImageDecoder`/`AnimatedImageDrawable` is API 28+ (this library ships
 * to API 24) and exposes no per-frame delays, and `Movie` is deprecated and time-seek based. Doing
 * it once in common code also means Android and iOS produce byte-identical frames from the same
 * GIF, which is what makes the round-trip tests comparable across platforms.
 *
 * [parse] only walks the block structure — no LZW, no pixels — so `getImageInfo` can report frame
 * count and duration for a 100 MB GIF without decoding it. [decodeFrames] composes frames onto a
 * single reused canvas and hands it to the caller one at a time; holding all frames would be
 * `width * height * 4 * frameCount` bytes (92 MB for a 48-frame 800x600 GIF).
 */
internal class GifDecoder
private constructor(
  private val data: ByteArray,
  val width: Int,
  val height: Int,
  val loopCount: Int,
  private val globalColorTable: IntArray?,
  private val frames: List<FrameMeta>,
) {

  val frameCount: Int
    get() = frames.size

  /** Total playback time of one loop, in milliseconds. */
  val durationMillis: Long
    get() = frames.sumOf { it.delayMillis.toLong() }

  val delaysMillis: IntArray
    get() = IntArray(frames.size) { frames[it].delayMillis }

  /** True when any frame declares a transparent colour index. */
  val hasTransparency: Boolean
    get() = frames.any { it.transparentIndex >= 0 }

  /**
   * Decodes frames in order, calling [onFrame] with the composed canvas as RGBA8888.
   *
   * The buffer is reused between frames — copy it if you need to keep it. Frames whose LZW data is
   * corrupt are skipped rather than aborting the whole animation, matching what browsers do with
   * truncated GIFs.
   */
  fun decodeFrames(onFrame: (index: Int, rgba: ByteArray, delayMillis: Int) -> Unit) {
    val canvas = ByteArray(width * height * 4)
    var restorePoint: ByteArray? = null
    var previous: FrameMeta? = null

    frames.forEachIndexed { index, frame ->
      previous?.let { prev ->
        when (prev.disposal) {
          DISPOSAL_BACKGROUND -> clearRect(canvas, prev)
          DISPOSAL_PREVIOUS -> restorePoint?.copyInto(canvas)
          else -> Unit
        }
      }
      if (frame.disposal == DISPOSAL_PREVIOUS) {
        restorePoint = canvas.copyOf()
      }

      if (drawFrame(canvas, frame)) {
        onFrame(index, canvas, frame.delayMillis)
      }
      previous = frame
    }
  }

  /** Paints [frame] onto [canvas]; false when its LZW stream could not be decoded. */
  private fun drawFrame(canvas: ByteArray, frame: FrameMeta): Boolean {
    val palette = frame.localColorTable ?: globalColorTable ?: return false
    val indices =
      decodeLzw(frame.lzwMinCodeSize, frame.dataStart, frame.width * frame.height) ?: return false

    // Walk the rows in the order they are STORED. For an interlaced frame that is not the order
    // they are displayed in, and the pass map only runs one way — stored index to output row.
    for (storedRow in 0 until frame.height) {
      val outputRow =
        if (frame.interlaced) interlacedOutputRow(storedRow, frame.height) else storedRow
      val canvasY = frame.top + outputRow
      if (canvasY < 0 || canvasY >= height) continue
      val sourceOffset = storedRow * frame.width
      var target = (canvasY * width + frame.left) * 4

      for (column in 0 until frame.width) {
        val canvasX = frame.left + column
        if (canvasX < 0 || canvasX >= width) {
          target += 4
          continue
        }
        val paletteIndex = indices[sourceOffset + column].toInt() and 0xFF
        if (paletteIndex != frame.transparentIndex && paletteIndex < palette.size) {
          val rgb = palette[paletteIndex]
          canvas[target] = (rgb shr 16 and 0xFF).toByte()
          canvas[target + 1] = (rgb shr 8 and 0xFF).toByte()
          canvas[target + 2] = (rgb and 0xFF).toByte()
          canvas[target + 3] = 0xFF.toByte()
        }
        target += 4
      }
    }
    return true
  }

  private fun clearRect(canvas: ByteArray, frame: FrameMeta) {
    for (row in 0 until frame.height) {
      val y = frame.top + row
      if (y < 0 || y >= height) continue
      val start = (y * width + frame.left.coerceIn(0, width)) * 4
      val end = (y * width + (frame.left + frame.width).coerceIn(0, width)) * 4
      canvas.fill(0, start, end)
    }
  }

  /**
   * GIF LZW: variable-width codes read out of the frame's sub-block chain, with a dictionary that
   * resets on the clear code. Returns palette indices, or null if the stream is unusable.
   */
  private fun decodeLzw(minCodeSize: Int, dataStart: Int, pixelCount: Int): ByteArray? {
    if (minCodeSize !in 2..8) return null
    val output = ByteArray(pixelCount)

    val clearCode = 1 shl minCodeSize
    val endCode = clearCode + 1
    val prefix = IntArray(MAX_CODES)
    val suffix = ByteArray(MAX_CODES)
    val stack = ByteArray(MAX_CODES)
    for (code in 0 until clearCode) {
      suffix[code] = code.toByte()
    }

    var codeSize = minCodeSize + 1
    var codeMask = (1 shl codeSize) - 1
    var available = clearCode + 2
    var oldCode = NO_CODE
    // First byte of the string `oldCode` expands to. The "code not in the dictionary yet" branch
    // below can only mean `oldCode` followed by that byte, so it has to be carried between codes.
    var first = 0

    var stackTop = 0
    var written = 0
    var bitBuffer = 0
    var bitCount = 0

    // Position inside the sub-block chain: `blockEnd` is one past the current sub-block's payload.
    var cursor = dataStart
    var blockEnd = dataStart

    while (written < pixelCount) {
      if (stackTop > 0) {
        output[written++] = stack[--stackTop]
        continue
      }

      while (bitCount < codeSize) {
        if (cursor >= blockEnd) {
          if (blockEnd >= data.size) return output
          val blockSize = data[blockEnd].toInt() and 0xFF
          if (blockSize == 0) return output
          cursor = blockEnd + 1
          blockEnd = cursor + blockSize
          if (blockEnd > data.size) return output
        }
        bitBuffer = bitBuffer or ((data[cursor++].toInt() and 0xFF) shl bitCount)
        bitCount += 8
      }

      val code = bitBuffer and codeMask
      bitBuffer = bitBuffer shr codeSize
      bitCount -= codeSize

      if (code == endCode || code > available) return output
      if (code == clearCode) {
        codeSize = minCodeSize + 1
        codeMask = (1 shl codeSize) - 1
        available = clearCode + 2
        oldCode = NO_CODE
        continue
      }
      if (oldCode == NO_CODE) {
        stack[stackTop++] = suffix[code]
        oldCode = code
        first = code
        continue
      }

      var current = code
      if (code == available) {
        stack[stackTop++] = first.toByte()
        current = oldCode
      }
      while (current >= clearCode) {
        if (stackTop >= stack.size) return output
        stack[stackTop++] = suffix[current]
        current = prefix[current]
      }
      first = suffix[current].toInt() and 0xFF
      stack[stackTop++] = suffix[current]

      if (available < MAX_CODES) {
        prefix[available] = oldCode
        suffix[available] = first.toByte()
        available++
        if (available and codeMask == 0 && available < MAX_CODES) {
          codeSize++
          codeMask += available
        }
      }
      oldCode = code
    }
    return output
  }

  private class FrameMeta(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val interlaced: Boolean,
    val localColorTable: IntArray?,
    val transparentIndex: Int,
    val disposal: Int,
    val delayMillis: Int,
    val lzwMinCodeSize: Int,
    val dataStart: Int,
  )

  companion object {
    private const val MAX_CODES = 4096
    private const val NO_CODE = -1
    private const val DISPOSAL_BACKGROUND = 2
    private const val DISPOSAL_PREVIOUS = 3

    /**
     * A zero or 1-centisecond delay means "as fast as possible" in practice; every browser and
     * every GIF renderer since Netscape clamps it to 100 ms instead of rendering thousands of
     * frames per second. Matching them keeps converted timing identical to what users saw.
     */
    private const val MIN_DELAY_CENTISECONDS = 2
    private const val DEFAULT_DELAY_MILLIS = 100

    /**
     * Reads the GIF's structure without decoding any pixels. Returns null when [data] is not a GIF
     * or its header is truncated.
     */
    fun parse(data: ByteArray): GifDecoder? {
      if (data.size < 13) return null
      if (
        data[0] != 'G'.code.toByte() || data[1] != 'I'.code.toByte() || data[2] != 'F'.code.toByte()
      ) {
        return null
      }

      val width = readLe16(data, 6)
      val height = readLe16(data, 8)
      if (width <= 0 || height <= 0) return null

      val packed = data[10].toInt() and 0xFF
      var offset = 13
      var globalColorTable: IntArray? = null
      if (packed and 0x80 != 0) {
        val size = 2 shl (packed and 0x07)
        globalColorTable = readColorTable(data, offset, size) ?: return null
        offset += size * 3
      }

      val frames = mutableListOf<FrameMeta>()
      var loopCount = 0
      var delayMillis = DEFAULT_DELAY_MILLIS
      var transparentIndex = -1
      var disposal = 0

      while (offset < data.size) {
        when (data[offset].toInt() and 0xFF) {
          0x21 -> {
            if (offset + 2 > data.size)
              return finish(data, width, height, loopCount, globalColorTable, frames)
            val label = data[offset + 1].toInt() and 0xFF
            var cursor = offset + 2
            if (label == 0xF9 && cursor + 5 <= data.size) {
              val flags = data[cursor + 1].toInt() and 0xFF
              disposal = (flags shr 2) and 0x07
              transparentIndex = if (flags and 0x01 != 0) data[cursor + 4].toInt() and 0xFF else -1
              val centiseconds = readLe16(data, cursor + 2)
              delayMillis =
                if (centiseconds < MIN_DELAY_CENTISECONDS) DEFAULT_DELAY_MILLIS
                else centiseconds * 10
            } else if (label == 0xFF && cursor + 12 <= data.size) {
              // NETSCAPE2.0 loop extension: sub-block 0x01, then a 16-bit repeat count.
              val isNetscape =
                (data[cursor].toInt() and 0xFF) == 11 &&
                  data.decodeToString(cursor + 1, cursor + 12).startsWith("NETSCAPE")
              if (isNetscape) {
                val sub = cursor + 12
                if (sub + 4 <= data.size && (data[sub].toInt() and 0xFF) >= 3) {
                  loopCount = readLe16(data, sub + 2)
                }
              }
            }
            cursor =
              skipSubBlocks(data, cursor)
                ?: return finish(data, width, height, loopCount, globalColorTable, frames)
            offset = cursor
          }
          0x2C -> {
            if (offset + 10 > data.size) break
            val left = readLe16(data, offset + 1)
            val top = readLe16(data, offset + 3)
            val frameWidth = readLe16(data, offset + 5)
            val frameHeight = readLe16(data, offset + 7)
            val framePacked = data[offset + 9].toInt() and 0xFF
            var cursor = offset + 10

            var localColorTable: IntArray? = null
            if (framePacked and 0x80 != 0) {
              val size = 2 shl (framePacked and 0x07)
              localColorTable = readColorTable(data, cursor, size) ?: break
              cursor += size * 3
            }
            if (cursor >= data.size) break
            val lzwMinCodeSize = data[cursor].toInt() and 0xFF
            cursor++

            if (frameWidth > 0 && frameHeight > 0) {
              frames +=
                FrameMeta(
                  left = left,
                  top = top,
                  width = frameWidth,
                  height = frameHeight,
                  interlaced = framePacked and 0x40 != 0,
                  localColorTable = localColorTable,
                  transparentIndex = transparentIndex,
                  disposal = disposal,
                  delayMillis = delayMillis,
                  lzwMinCodeSize = lzwMinCodeSize,
                  dataStart = cursor,
                )
            }
            // Graphic control state applies to the next image block only.
            delayMillis = DEFAULT_DELAY_MILLIS
            transparentIndex = -1
            disposal = 0
            offset = skipSubBlocks(data, cursor) ?: break
          }
          0x3B -> break
          else -> offset++
        }
      }

      return finish(data, width, height, loopCount, globalColorTable, frames)
    }

    /** True when [data] is a GIF carrying more than one frame. */
    fun isAnimated(data: ByteArray): Boolean = (parse(data)?.frameCount ?: 0) > 1

    private fun finish(
      data: ByteArray,
      width: Int,
      height: Int,
      loopCount: Int,
      globalColorTable: IntArray?,
      frames: List<FrameMeta>,
    ): GifDecoder? =
      if (frames.isEmpty()) null
      else GifDecoder(data, width, height, loopCount, globalColorTable, frames)

    private fun readColorTable(data: ByteArray, offset: Int, entries: Int): IntArray? {
      if (offset + entries * 3 > data.size) return null
      return IntArray(entries) { i ->
        val base = offset + i * 3
        ((data[base].toInt() and 0xFF) shl 16) or
          ((data[base + 1].toInt() and 0xFF) shl 8) or
          (data[base + 2].toInt() and 0xFF)
      }
    }

    /**
     * Walks a chain of length-prefixed sub-blocks and returns the offset just past its terminator.
     */
    private fun skipSubBlocks(data: ByteArray, start: Int): Int? {
      var offset = start
      while (offset < data.size) {
        val size = data[offset].toInt() and 0xFF
        if (size == 0) return offset + 1
        offset += size + 1
      }
      return null
    }

    private fun readLe16(data: ByteArray, offset: Int): Int {
      if (offset + 2 > data.size) return 0
      return (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
    }

    /**
     * Where the [storedRow]-th row of an interlaced frame belongs on screen. GIF stores rows in
     * four passes: every 8th row from 0, then every 8th from 4, then every 4th from 2, then every
     * 2nd from 1.
     */
    private fun interlacedOutputRow(storedRow: Int, height: Int): Int {
      val pass1 = (height + 7) / 8
      val pass2 = pass1 + (height + 3) / 8
      val pass3 = pass2 + (height + 1) / 4
      return when {
        storedRow < pass1 -> storedRow * 8
        storedRow < pass2 -> (storedRow - pass1) * 8 + 4
        storedRow < pass3 -> (storedRow - pass2) * 4 + 2
        else -> (storedRow - pass3) * 2 + 1
      }
    }
  }
}
