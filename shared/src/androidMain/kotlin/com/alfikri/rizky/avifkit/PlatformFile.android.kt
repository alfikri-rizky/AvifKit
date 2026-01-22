package com.alfikri.rizky.avifkit

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android implementation of PlatformFile
 * Supports both File paths and content:// URIs
 */
actual class PlatformFile private constructor(
    private val context: Context?,
    private val file: File?,
    private val uri: Uri?
) {
    actual val name: String
        get() = when {
            file != null -> file.name
            uri != null && context != null -> getUriFileName(context, uri)
            else -> "unknown"
        }

    actual val extension: String
        get() = name.substringAfterLast('.', "")

    actual val path: String
        get() = when {
            file != null -> file.absolutePath
            uri != null -> uri.toString()
            else -> ""
        }

    actual suspend fun readBytes(): ByteArray = withContext(Dispatchers.IO) {
        when {
            file != null -> file.readBytes()
            uri != null && context != null -> {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw AvifError.FileError("Cannot open input stream for URI: $uri")
            }
            else -> throw AvifError.FileError("Invalid PlatformFile state")
        }
    }

    actual suspend fun writeBytes(data: ByteArray): Unit = withContext(Dispatchers.IO) {
        when {
            file != null -> {
                file.parentFile?.mkdirs()
                file.writeBytes(data)
            }
            uri != null && context != null -> {
                context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                    outputStream.write(data)
                } ?: throw AvifError.FileError("Cannot open output stream for URI: $uri")
            }
            else -> throw AvifError.FileError("Invalid PlatformFile state")
        }
    }

    actual suspend fun size(): Long = withContext(Dispatchers.IO) {
        when {
            file != null -> file.length()
            uri != null && context != null -> getUriFileSize(context, uri)
            else -> 0L
        }
    }

    actual suspend fun exists(): Boolean = withContext(Dispatchers.IO) {
        when {
            file != null -> file.exists()
            uri != null && context != null -> {
                try {
                    context.contentResolver.openInputStream(uri)?.use { true } ?: false
                } catch (e: Exception) {
                    false
                }
            }
            else -> false
        }
    }

    actual override fun toString(): String = path

    actual companion object {
        private var appContext: Context? = null

        /**
         * Initialize with application context
         * Call this once in Application.onCreate()
         */
        fun init(context: Context) {
            appContext = context.applicationContext
        }

        actual fun fromPath(path: String): PlatformFile {
            return when {
                path.startsWith("content://") || path.startsWith("file://") -> {
                    val uri = Uri.parse(path)
                    PlatformFile(appContext, null, uri)
                }
                else -> {
                    val file = File(path)
                    PlatformFile(null, file, null)
                }
            }
        }

        /**
         * Create from File object
         */
        fun fromFile(file: File): PlatformFile {
            return PlatformFile(null, file, null)
        }

        /**
         * Create from URI (requires context)
         */
        fun fromUri(context: Context, uri: Uri): PlatformFile {
            return PlatformFile(context, null, uri)
        }
    }

    private fun getUriFileName(context: Context, uri: Uri): String {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)
            } else {
                uri.lastPathSegment ?: "unknown"
            }
        } ?: uri.lastPathSegment ?: "unknown"
    }

    private fun getUriFileSize(context: Context, uri: Uri): Long {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0 && cursor.moveToFirst()) {
                cursor.getLong(sizeIndex)
            } else {
                0L
            }
        } ?: 0L
    }
}

actual val PlatformFile.nameWithoutExtension: String
    get() = name.substringBeforeLast('.', name)