package com.alfikri.rizky.avifkit.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.IOException

object FileDownloadHelper {

    /**
     * Saves a file to the Downloads folder using MediaStore API
     * @param context Android context
     * @param sourceFile The file to be saved
     * @param displayName The name to display in Downloads folder
     * @return Result containing success status and message
     */
    fun saveToDownloads(
        context: Context,
        sourceFile: File,
        displayName: String = "converted_image_${System.currentTimeMillis()}.avif"
    ): Result<String> {
        return try {
            if (!sourceFile.exists()) {
                return Result.failure(IOException("Source file does not exist"))
            }

            val resolver = context.contentResolver

            // Set up content values for the new file
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/avif")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)

                // For Android 10+, mark as pending while we write
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            // Insert the file into MediaStore
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Files.getContentUri("external")
            }

            val uri = resolver.insert(collection, contentValues)
                ?: return Result.failure(IOException("Failed to create media store entry"))

            // Write the file content
            resolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return Result.failure(IOException("Failed to open output stream"))

            // Mark as completed (no longer pending)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            Result.success("File saved to Downloads/$displayName")
        } catch (e: Exception) {
            Result.failure(IOException("Failed to save file: ${e.message}", e))
        }
    }

    /**
     * Generates a user-friendly filename for the downloaded AVIF file
     */
    fun generateAvifFileName(originalFileName: String? = null): String {
        val timestamp = System.currentTimeMillis()
        val baseName = originalFileName?.substringBeforeLast(".")?.take(20) ?: "image"
        return "${baseName}_$timestamp.avif"
    }
}
