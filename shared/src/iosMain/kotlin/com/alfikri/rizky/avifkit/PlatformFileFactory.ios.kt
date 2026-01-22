package com.alfikri.rizky.avifkit

import io.github.vinceglb.filekit.*
import platform.Foundation.NSURL

actual object PlatformFileFactory {
    actual fun fromPath(path: String): PlatformFile {
        // On iOS, FileKit's PlatformFile accepts an NSURL
        val url = NSURL.fileURLWithPath(path)
        return PlatformFile(url)
    }
}
