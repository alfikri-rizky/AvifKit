package com.alfikri.rizky.avifkit

/**
 * Sealed class representing AVIF conversion errors
 */
sealed class AvifError : Exception() {
    data object UnsupportedFormat : AvifError() {
        override val message: String = "Unsupported image format"
        private fun readResolve(): Any = UnsupportedFormat
    }

    data class EncodingFailed(override val message: String) : AvifError()

    data class DecodingFailed(override val message: String) : AvifError()

    data object OutOfMemory : AvifError() {
        override val message: String = "Out of memory during AVIF processing"
        private fun readResolve(): Any = OutOfMemory
    }

    data object InvalidInput : AvifError() {
        override val message: String = "Invalid input data"
        private fun readResolve(): Any = InvalidInput
    }

    data class FileError(override val message: String) : AvifError()

    data class Unknown(override val message: String) : AvifError()
}
