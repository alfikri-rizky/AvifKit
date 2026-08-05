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
├── androidMain/kotlin/.../    # Android actual implementations (JNI calls into :shared-native)
├── iosMain/kotlin/.../        # iOS actual implementations (call libavif via cinterop)
├── nativeInterop/cinterop/    # libavif.def — Kotlin/Native binding to libavif's C API
├── commonTest/                # Shared tests (run on JVM host + iOS simulator)
├── iosTest/                   # iOS-only round-trip test against the real codec
└── androidDeviceTest/         # Instrumented: the JNI path needs a device/emulator
```

The C++ does **not** live here. The JNI wrapper and the libavif/AOM build are in
`:shared-native/src/main/cpp/` (see its own AGENTS.md), because the AGP KMP library plugin
cannot build CMake/NDK code.

iOS calls libavif directly via cinterop (no Swift). Codec static libs are built by
`scripts/build-ios-libavif.sh` into `src/nativeInterop/libs/ios/<target>/`.

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
2. Check `options.maxSize` → if set, hand off to `AdaptiveCompression` (shared by both platforms)
3. `CompressionStrategy.SMART` → binary search over quality for the highest that still fits
4. `CompressionStrategy.STRICT` → progressively more aggressive settings for the smallest result
5. If neither reaches the target, `AdaptiveCompression.fallbackOptions()` — still AVIF, just
   aggressive (q40, speed 10, YUV420, 1024 px). `maxSize` is best-effort by contract: the
   smallest achieved result is returned rather than throwing.

**There is no JPEG fallback anywhere in this module.** When the native codec is missing or fails,
the library throws `AvifError` — see "Explicit Error Reporting" in the README. An encoder that
silently returned JPEG bytes from a function called `encodeAvif` would be worse than an error,
because the caller would write them to a `.avif` file and only find out downstream.

## BUILD CONFIG

- Version: `avifkit` in `gradle/libs.versions.toml` — the single source of truth for both
  `:shared` and `:shared-native`, which must ship as a matching pair. CI overrides it with
  `-PVERSION_NAME` (see `.github/workflows/publish.yml`).
- Maven group: `io.github.alfikri-rizky`
- XCFramework name: `Shared` (dynamic, so the Kotlin runtime is a single dyld image)
- iOS targets: iosArm64, iosX64, iosSimulatorArm64
- The libavif/AOM static libs are embedded *into* the cinterop klib via `-staticLibrary`, so a
  pure-Gradle KMP consumer links with no SPM and no Xcode setup.

## DEPENDENCIES

Versions come from `gradle/libs.versions.toml`; the ones below are what it currently pins.

- `kotlinx-coroutines-core:1.11.0` — async operations
- `filekit-core:0.12.0` — cross-platform file handling, exposed as `api()` so consumers get
  `PlatformFile` (the app modules depend on that; bumping it is a consumer-visible change)
- `kotlinx-io-core:0.8.2` — I/O operations
- Android: `exifinterface:1.4.2` — EXIF orientation
- Android: `:shared-native` — runtime-only, carries the `.so`; published as `avifkit-native`

## CONSUMERS IN THIS REPO

`:composeApp` (AVIF Studio's shared Compose UI) depends on this module and is the reference for
how the API is meant to be used end to end — batch encoding, decoding for display on OS versions
that cannot render AVIF, and adaptive size targeting.
