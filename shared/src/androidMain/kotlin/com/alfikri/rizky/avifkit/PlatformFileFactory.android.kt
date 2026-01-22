package com.alfikri.rizky.avifkit

import io.github.vinceglb.filekit.*
import java.io.File

actual object PlatformFileFactory {
    actual fun fromPath(path: String): PlatformFile {
        // On Android, FileKit's PlatformFile accepts a java.io.File
        return PlatformFile(File(path))
    }
}
