package com.alfikri.rizky.avifkit

import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.*

/**
 * iOS implementation of PlatformFile
 * Wraps NSURL for file system access
 */
@OptIn(ExperimentalForeignApi::class)
actual class PlatformFile internal constructor(
    private val url: NSURL
) {
    actual val name: String
        get() = url.lastPathComponent ?: "unknown"

    actual val extension: String
        get() = url.pathExtension ?: ""

    actual val path: String
        get() = url.path ?: url.absoluteString ?: ""

    actual suspend fun readBytes(): ByteArray = withContext(Dispatchers.IO) {
        val data = NSData.dataWithContentsOfURL(url)
            ?: throw AvifError.FileError("Cannot read file: $path")

        ByteArray(data.length.toInt()).apply {
            usePinned { pinned ->
                data.getBytes(pinned.addressOf(0))
            }
        }
    }

    actual suspend fun writeBytes(data: ByteArray): Unit = withContext(Dispatchers.IO) {
        // Create parent directory if needed
        url.URLByDeletingLastPathComponent?.let { parentURL ->
            val fileManager = NSFileManager.defaultManager
            val parentPath = parentURL.path
            if (parentPath != null && !fileManager.fileExistsAtPath(parentPath)) {
                fileManager.createDirectoryAtURL(
                    parentURL,
                    withIntermediateDirectories = true,
                    attributes = null,
                    error = null
                )
            }
        }

        // Write data
        val nsData = data.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = data.size.toULong())
        }

        val success = nsData.writeToURL(url, atomically = true)
        if (!success) {
            throw AvifError.FileError("Cannot write to file: $path")
        }
    }

    actual suspend fun size(): Long = withContext(Dispatchers.IO) {
        val fileManager = NSFileManager.defaultManager
        val attributes = fileManager.attributesOfItemAtPath(url.path ?: "", error = null)
        (attributes?.get(NSFileSize) as? NSNumber)?.longValue ?: 0L
    }

    actual suspend fun exists(): Boolean = withContext(Dispatchers.IO) {
        val fileManager = NSFileManager.defaultManager
        url.path?.let { fileManager.fileExistsAtPath(it) } ?: false
    }

    actual override fun toString(): String = path

    /**
     * Start accessing a security-scoped resource
     * Required for files obtained through document picker
     */
    fun startAccessingSecurityScopedResource(): Boolean {
        return url.startAccessingSecurityScopedResource()
    }

    /**
     * Stop accessing a security-scoped resource
     */
    fun stopAccessingSecurityScopedResource() {
        url.stopAccessingSecurityScopedResource()
    }

    /**
     * Execute a block with security-scoped access
     * Automatically manages resource access lifecycle
     */
    inline fun <T> withScopedAccess(block: (PlatformFile) -> T): T {
        val didStart = startAccessingSecurityScopedResource()
        try {
            return block(this)
        } finally {
            if (didStart) {
                stopAccessingSecurityScopedResource()
            }
        }
    }

    actual companion object {
        actual fun fromPath(path: String): PlatformFile {
            val url = when {
                path.startsWith("file://") -> NSURL(string = path)
                else -> NSURL.fileURLWithPath(path)
            }
            return PlatformFile(url)
        }

        /**
         * Create from NSURL
         */
        fun fromURL(url: NSURL): PlatformFile {
            return PlatformFile(url)
        }
    }
}

actual val PlatformFile.nameWithoutExtension: String
    get() = name.substringBeforeLast('.', name)