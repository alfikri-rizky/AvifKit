// Re-export the Kotlin Shared framework's public symbols so consumers can
// reach types like AvifConverter, EncodingOptions, ImageInput, AvifError,
// Priority, and KotlinByteArray with a single `import AvifKit` — without
// needing a separate `import Shared` link line that would cause the static
// Shared.xcframework to be linked into the consumer binary twice and split
// the Kotlin singletons (see Package.swift comment on the AvifKit product).
@_exported import Shared
