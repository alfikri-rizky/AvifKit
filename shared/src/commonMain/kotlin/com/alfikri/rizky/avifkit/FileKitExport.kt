package com.alfikri.rizky.avifkit

// Re-export FileKit PlatformFile
import io.github.vinceglb.filekit.PlatformFile as FileKitPlatformFile

// Re-export all FileKit extension functions and properties
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.nameWithoutExtension
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.size
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.readString
import io.github.vinceglb.filekit.write
import io.github.vinceglb.filekit.writeString
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.mimeType
import io.github.vinceglb.filekit.source
import io.github.vinceglb.filekit.sink

/**
 * Type alias to expose FileKit's PlatformFile from the shared module
 * This allows other modules to use FileKit by importing from the shared module
 */
typealias PlatformFile = FileKitPlatformFile

/**
 * Factory object to create PlatformFile instances
 * This provides a Swift-friendly API since typealiases don't export to Swift
 */
expect object PlatformFileFactory {
    /**
     * Create a PlatformFile from a path string
     */
    fun fromPath(path: String): PlatformFile
}

/**
 * Helper object to expose FileKit extension functions to Swift
 * Swift doesn't see Kotlin extension functions, so we wrap them
 */
object PlatformFileHelper {
    /**
     * Get the file size in bytes
     */
    suspend fun getSize(file: PlatformFile): Long = file.size()

    /**
     * Get the file path
     */
    fun getPath(file: PlatformFile): String = file.path

    /**
     * Get the file name
     */
    fun getName(file: PlatformFile): String = file.name
}
