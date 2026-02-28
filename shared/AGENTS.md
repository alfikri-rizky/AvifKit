# SHARED MODULE - KMP LIBRARY CORE

## OVERVIEW

Published Kotlin Multiplatform library (`io.github.alfikri-rizky:avifkit`). Contains cross-platform API and platform-specific implementations.

## STRUCTURE

```
shared/src/
├── commonMain/kotlin/.../     # Cross-platform API (expect declarations)
│   ├── AvifConverter.kt       # Main API (expect class)
│   ├── Models.kt              # Data classes, enums
│   ├── AvifError.kt           # Error types
│   └── FileKitExport.kt       # PlatformFile typealias
├── androidMain/
│   ├── kotlin/.../            # Android actual implementations
│   └── cpp/                   # JNI wrapper + libavif (see cpp/AGENTS.md)
├── iosMain/
│   ├── kotlin/.../            # iOS actual implementations
│   └── swift/                 # AVIFNativeConverter.swift
└── commonTest/                # Shared tests
```

## WHERE TO LOOK

| Task | File | Notes |
|------|------|-------|
| Add new API method | `commonMain/AvifConverter.kt` | Add expect declaration first |
| Implement for Android | `androidMain/AvifConverter.android.kt` | actual implementation |
| Implement for iOS | `iosMain/AvifConverter.ios.kt` | actual implementation |
| Add encoding option | `commonMain/Models.kt` | Update EncodingOptions data class |
| Add error type | `commonMain/AvifError.kt` | Add to sealed class |
| Platform bitmap type | `{platform}Main/PlatformBitmap.{platform}.kt` | Typealias to native type |

## EXPECT/ACTUAL PATTERN

```kotlin
// commonMain: Define interface
expect class AvifConverter() {
    suspend fun encodeAvif(input: ImageInput, ...): ByteArray
}

// androidMain: Android implementation
actual class AvifConverter {
    actual suspend fun encodeAvif(...) = withContext(Dispatchers.IO) { ... }
}

// iosMain: iOS implementation  
actual class AvifConverter {
    actual suspend fun encodeAvif(...) = withContext(Dispatchers.Default) { ... }
}
```

## PLATFORM BITMAP MAPPING

| Platform | PlatformBitmap | Import |
|----------|----------------|--------|
| Android | `android.graphics.Bitmap` | typealias |
| iOS | `platform.UIKit.UIImage` | typealias |

## COMPRESSION FLOW

1. `convertToFile()` / `encodeAvif()` called
2. Check `options.maxSize` → if set, use adaptive compression
3. `CompressionStrategy.SMART` → binary search for highest quality
4. `CompressionStrategy.STRICT` → exhaustive search for smallest size
5. Fallback to JPEG if native library unavailable

## BUILD CONFIG

- Version: line 12 in `build.gradle.kts`
- Maven group: `io.github.alfikri-rizky`
- XCFramework name: `Shared`
- iOS targets: iosArm64, iosX64, iosSimulatorArm64

## DEPENDENCIES

- `kotlinx-coroutines-core:1.8.0` - async operations
- `filekit-core:0.12.0` - cross-platform file handling
- `kotlinx-io-core:0.8.2` - I/O operations
- Android: `exifinterface:1.3.7` - EXIF orientation
