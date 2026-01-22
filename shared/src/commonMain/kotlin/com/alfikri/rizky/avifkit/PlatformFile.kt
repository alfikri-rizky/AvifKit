package com.alfikri.rizky.avifkit

/**
 * Multiplatform file abstraction for AvifKit
 * Provides a unified interface for file operations across Android and iOS
 */
expect class PlatformFile {
    /**
     * The name of the file including extension
     */
    val name: String

    /**
     * The file extension (e.g., "avif", "jpg")
     */
    val extension: String

    /**
     * The full path or URI representation of this file
     */
    val path: String

    /**
     * Read the complete file content as bytes
     */
    suspend fun readBytes(): ByteArray

    /**
     * Write bytes to this file, creating or overwriting as needed
     */
    suspend fun writeBytes(data: ByteArray)

    /**
     * Get the file size in bytes
     */
    suspend fun size(): Long

    /**
     * Check if the file exists
     */
    suspend fun exists(): Boolean

    override fun toString(): String

    companion object {
        /**
         * Create a PlatformFile from a path/URI string
         */
        fun fromPath(path: String): PlatformFile
    }
}

/**
 * Extension property for name without extension
 */
expect val PlatformFile.nameWithoutExtension: String