# AVIFKIT SKILL

**Version:** 0.2.6
**Domain:** Kotlin Multiplatform → AVIF image encoding/decoding for Android & iOS
**Maturity:** Production (published to Maven Central + SPM)

---

## 1. OVERVIEW

Kotlin Multiplatform library (`io.github.alfikri-rizky:avifkit`) that converts images to/from AVIF format. Targets Android (JNI + libavif AOM codec) and iOS (Swift + avif.swift wrapping aom encoder + dav1d decoder). Published as a Kotlin multiplatform artifact + SPM-distributed XCFramework.

**Key architectural decision:** Two-tier native implementation — libavif on Android via JNI, avif.swift on iOS via Swift bridge. Both use pre-built native libraries (no source compilation by consumers).

---

## 2. PROJECT MAP

```
AvifKit/
├── shared/                          # ★ KMP LIBRARY (published artifact)
│   ├── src/
│   │   ├── commonMain/kotlin/.../   # Cross-platform API (expect declarations)
│   │   │   ├── AvifConverter.kt     # Main API — expect class
│   │   │   ├── Models.kt            # EncodingOptions, Priority, ImageInput, etc.
│   │   │   ├── AvifError.kt         # Sealed error hierarchy
│   │   │   ├── PlatformBitmap.kt    # expect class (Bitmap/UIImage typealias)
│   │   │   ├── FileKitExport.kt     # PlatformFile typealias + helper
│   │   │   └── AvifSamples.kt       # Usage examples
│   │   ├── androidMain/kotlin/.../  # Android actual implementations
│   │   │   ├── AvifConverter.android.kt   # actual class (~727 lines)
│   │   │   ├── PlatformBitmap.android.kt  # typealias = Bitmap
│   │   │   └── PlatformFileFactory.android.kt
│   │   ├── androidMain/cpp/         # Native C++ JNI wrapper
│   │   │   ├── CMakeLists.txt       # Conditional build (HAVE_LIBAVIF=0|1)
│   │   │   └── avif_jni_wrapper.cpp # JNI encode/decode with libavif
│   │   ├── iosMain/kotlin/.../      # iOS actual implementations
│   │   │   ├── AvifConverter.ios.kt # actual class (~608 lines)
│   │   │   ├── IosAvifNativeHandler.kt # Swift bridge interface + auto-discovery
│   │   │   ├── PlatformBitmap.ios.kt   # typealias = UIImage
│   │   │   └── PlatformFileFactory.ios.kt
│   │   ├── iosMain/swift/           # Swift native code (in SPM target)
│   │   │   └── AVIFNativeConverter.swift  # avif.swift wrapper (~209 lines)
│   │   └── iosMain/objc/            # ObjC auto-registration
│   │       ├── AvifKitAutoRegister.m   # __attribute__((constructor))
│   │       └── include/AvifKitObjC.h
│   └── build.gradle.kts             # Version, deps, Maven Central config
├── composeApp/                      # Android demo app (NOT published)
├── iosApp/                          # iOS demo app (NOT published)
├── Package.swift                    # SPM distribution manifest
├── AvifKit.podspec                  # CocoaPods distribution (not recommended)
├── settings.gradle.kts
├── build.gradle.kts                 # Root
└── gradle/libs.versions.toml        # Version catalog
```

---

## 3. WHERE TO LOOK FOR WHAT

| Task | File(s) | Notes |
|------|---------|-------|
| **Add/change API method** | `commonMain/AvifConverter.kt` → add expect, then actual in both platform dirs | Expect/actual pattern |
| **Add encoding option** | `commonMain/Models.kt` → `EncodingOptions` | Update data class + init validation |
| **Add error type** | `commonMain/AvifError.kt` → sealed class | Add new subclass |
| **Android native logic** | `androidMain/AvifConverter.android.kt` | Kotlin dispatcher + JNI calls |
| **Android C++/JNI** | `androidMain/cpp/avif_jni_wrapper.cpp` | libavif encode/decode via JNI |
| **Android build** | `androidMain/cpp/CMakeLists.txt` | Conditional HAVE_LIBAVIF, 16KB page alignment |
| **iOS native logic** | `iosMain/AvifConverter.ios.kt` | Kotlin dispatcher + bridge calls |
| **iOS Swift native** | `iosMain/swift/AVIFNativeConverter.swift` | avif.swift encode/decode |
| **iOS handler bridge** | `iosMain/IosAvifNativeHandler.kt` | Interface + ObjC runtime auto-discovery |
| **iOS auto-reg** | `iosMain/objc/AvifKitAutoRegister.m` | `__attribute__((constructor))` |
| **Bump version** | `shared/build.gradle.kts` line 12 + `Package.swift` checksum | Also update podspec if needed |
| **Add dependency** | `gradle/libs.versions.toml` | Version catalog |
| **CI/CD** | `.github/workflows/` | publish.yml (Maven Central) + publish-ios.yml (XCFramework) |
| **Change iOS deployment** | `shared/build.gradle.kts` (linkerOpts) + `Package.swift` + `AvifKit.podspec` | Hardcoded 15.0 in multiple places |
| **Tests** | `shared/src/commonTest/` | Currently minimal |

---

## 4. CORE API REFERENCE

### AvifConverter (expect class)

```kotlin
expect class AvifConverter() {
    suspend fun convertToBitmap(input: ImageInput, priority: Priority = BALANCED, options: EncodingOptions? = null): PlatformBitmap
    suspend fun convertToFile(input: ImageInput, outputPath: String, priority: Priority = BALANCED, options: EncodingOptions? = null): String
    suspend fun convertToFile(input: ImageInput, output: PlatformFile, priority: Priority = BALANCED, options: EncodingOptions? = null): PlatformFile
    suspend fun encodeAvif(input: ImageInput, priority: Priority = BALANCED, options: EncodingOptions? = null): ByteArray
    suspend fun decodeAvif(input: ImageInput): PlatformBitmap
    fun isAvifSupported(): Boolean
    fun isAvifFile(input: ImageInput): Boolean
    suspend fun getImageInfo(input: ImageInput): ImageInfo
}
```

All public methods have `@Throws(Exception::class)` — errors propagate as `NSError` to Swift.

### ImageInput

```kotlin
sealed class ImageInput {
    class FromBytes(data: ByteArray)
    class FromBitmap(bitmap: PlatformBitmap)  // PlatformBitmap = android.graphics.Bitmap | UIImage
    class FromPath(path: String)
    class FromFile(file: PlatformFile)
    companion object { fun from(...) }  // Factory overloads for all types
}
```

### EncodingOptions

```kotlin
data class EncodingOptions(
    val quality: Int = 75,         // 0-100
    val speed: Int = 6,            // 0-10
    val subsample: ChromaSubsample = YUV420,
    val alphaQuality: Int = 90,    // 0-100
    val lossless: Boolean = false,
    val preserveMetadata: Boolean = false,
    val maxDimension: Int? = null,
    val maxSize: Long? = null,     // triggers adaptive compression
    val compressionStrategy: CompressionStrategy = SMART
)
```

### Priority Presets

| Preset | Quality | Speed | Subsample | Alpha | Max Dim |
|--------|---------|-------|-----------|-------|---------|
| SPEED | 70 | 10 | YUV420 | 75 | 1920 |
| BALANCED (default) | 80 | 6 | YUV420 | 85 | 2048 |
| QUALITY | 95 | 5 | YUV444 | 98 | 4096 |
| STORAGE | 65 | 8 | YUV420 | 70 | 1280 |

### Compression Strategies

| Strategy | Goal | Algorithm | Attempts |
|----------|------|-----------|----------|
| SMART (default) | Best quality within target size | Binary search quality 40-100 | 6-8 |
| STRICT | Smallest possible size | Aggressive progressive | Up to 10 |

### Error Hierarchy

```
AvifError : Exception()  (sealed)
├── UnsupportedFormat   (object)
├── EncodingFailed(msg) (data class)
├── DecodingFailed(msg) (data class)
├── OutOfMemory         (object)
├── InvalidInput        (object)
├── FileError(msg)      (data class)
└── Unknown(msg)        (data class)
```

---

## 5. ARCHITECTURE & FLOW

### Encoding Flow

```
convertToFile() / encodeAvif()
  │
  ├─ options.maxSize != null ──► convertWithAdaptiveCompression()
  │                                ├─ SMART ──► binary search (quality 40-100)
  │                                └─ STRICT ──► progressive adjustments (up to 10 attempts)
  │
  └─ options.maxSize == null ──► convertStandard()
       │
       ├─ Input parsing (ByteArray / Bitmap / Path / File)
       ├─ Format detection + passthrough for AVIF
       ├─ EXIF orientation handling (Android only)
       ├─ Resize if maxDimension set
       └─ Platform-specific encode:
            ├─ Android: bitmapToByteArray() → nativeEncode() JNI → libavif
            └─ iOS: encodeImageToAvif() → handler.encodeImageWithOptions() → avif.swift
```

### iOS Native Bridge Architecture

```
Kotlin/Native (AvifConverter.ios.kt)
  └─ calls AvifKitIos.getOrDiscoverHandler()
       ├─ Fast path: already registered handler
       └─ Slow path: ObjC runtime NSClassFromString("AvifKit.AvifKitSetup")
            → calls registerNativeHandler()
            → creates AvifKitNativeHandler (Swift)
            → wraps AVIFNativeConverter (Swift)
            → delegates to avif.swift library
```

The ObjC `__attribute__((constructor))` in `AvifKitAutoRegister.m` runs auto-discovery at library load time. The Kotlin side also does lazy ObjC runtime discovery as a second line of defense.

---

## 6. CONVENTIONS & PATTERNS

### Expect/Actual Pattern
- **commonMain:** Define `expect class` with method signatures
- **androidMain:** `actual class` — JNI-based, Dispatchers.IO
- **iosMain:** `actual class` — Swift bridge, Dispatchers.Default

### Platform Bitmap Mapping
| commonMain | Android | iOS |
|---|---|---|
| `expect class PlatformBitmap` | `actual typealias PlatformBitmap = Bitmap` | `actual typealias PlatformBitmap = UIImage` |

### Code Style
- Package: `com.alfikri.rizky.avifkit`
- Kotlin official style (`kotlin.code.style=official`)
- Java 11 JVM target
- C++17 standard for native code
- Swift 5.0+, iOS 15.0+

### PlatformFile (FileKit)
- Cross-platform file abstraction via `io.github.vinceglb:filekit-core:0.12.0`
- Android: wraps `java.io.File`
- iOS: wraps `NSURL`
- Re-exported as `typealias PlatformFile` from `FileKitExport.kt`
- `PlatformFileHelper` exposes `size()`, `path`, `name` to Swift (which can't see Kotlin extension functions)

### Concurrent Pattern
- Android: `withContext(Dispatchers.IO)` for all suspend functions
- iOS: `withContext(Dispatchers.Default)` for all suspend functions

### Error Handling
- Native methods return `null` on failure → Kotlin wraps in `AvifError`
- `OutOfMemoryError` caught specifically → rethrown as `AvifError.OutOfMemory`
- AvifError propagated to Swift as `NSError` via `@Throws`

---

## 7. ANDROID NATIVE (C++ / JNI)

### Files
- `avif_jni_wrapper.cpp` — JNI implementations of `nativeEncode`, `nativeDecode`, `nativeIsAvif`, `nativeGetVersion`
- `CMakeLists.txt` — conditional build, links libavif or uses placeholder

### Conditional Compilation
```cmake
# With libavif (after running setup script)
add_compile_definitions(HAVE_LIBAVIF=1)

# Without libavif (placeholder mode)
add_compile_definitions(HAVE_LIBAVIF=0)
```

When `HAVE_LIBAVIF=0`:
- `nativeEncode` returns mock AVIF header bytes (12-byte ftyp box)
- `nativeDecode` returns a 100x100 gradient test pattern
- `nativeGetVersion` returns "Placeholder (libavif not integrated)"

### JNI Pixel Format
- Input: RGBA byte array (4 bytes per pixel: R, G, B, A)
- Output: int[] of ARGB_8888 pixels (Android Bitmap format)
- libavif uses YUV internally via `avifImageRGBToYUV()` / `avifImageYUVToRGB()`

### 16KB Page Alignment (Android 15+)
```cmake
# CORRECT: Target-specific (line 98-103 of CMakeLists.txt)
target_link_options(avif-android-wrapper PRIVATE "-Wl,-z,max-page-size=16384")

# NEVER use global flags — breaks libavif/AOM memory layout:
# set(CMAKE_SHARED_LINKER_FLAGS "... -Wl,-z,max-page-size=16384")  ← WRONG
```

### EXIF Orientation (Android)
- Read via `androidx.exifinterface:ExifInterface`
- Applies rotation/flip matrix before encoding
- Handles all 8 EXIF orientation values
- `ORIENTATION_NORMAL` and `UNDEFINED` are no-ops

---

## 8. iOS NATIVE (Swift / ObjC)

### Files
- `AVIFNativeConverter.swift` — Swift wrapper around avif.swift
- `AvifKitAutoRegister.m` — `__attribute__((constructor))` auto-registration
- `IosAvifNativeHandler.kt` — interface + ObjC runtime discovery

### avif.swift Integration
- `AVIFEncoder.encode(image:quality:speed:)` for encoding
- `AVIFDecoder.decode(_:)` for decoding
- Speed clamped to max 6 (speed ≥ 7 triggers AOM_USAGE_REALTIME, incompatible with still image encoding)
- Quality: 0.0-1.0 normalized (Kotlin passes 0-100, divided by 100)

### Orientation Handling (iOS)
- `normalizeOrientation()` redraws UIImage to apply orientation
- UIImage orientation handled natively by avif.swift
- Not applied if already `.up` orientation (fast path)

### Auto-Registration
- `AvifKitAutoRegister.m` uses `__attribute__((constructor))` — runs after all ObjC/Swift +load methods
- Tries both `"AvifKit.AvifKitSetup"` (module-prefixed) and `"AvifKitSetup"` (non-prefixed)
- Falls back to Kotlin-side lazy ObjC runtime discovery in `AvifKitIos.getOrDiscoverHandler()`

---

## 9. BUILD & COMMANDS

### Development

```bash
# Build entire project
./gradlew :shared:build

# Run shared tests
./gradlew :shared:test

# Android demo app
./gradlew :composeApp:assembleDebug

# iOS XCFramework
./gradlew :shared:assembleSharedReleaseXCFramework
```

### Publishing

```bash
# Maven Central (requires credentials)
./gradlew :shared:publishAllPublicationsToSonatypeRepository

# Local Maven for testing
./gradlew :shared:publishToMavenLocal

# iOS XCFramework build
./gradlew :shared:assembleSharedReleaseXCFramework
# Then zip: zip -r Shared.xcframework.zip Shared.xcframework
# Upload to GitHub Release, update Package.swift checksum

# CocoaPods (not recommended)
pod trunk push AvifKit.podspec
```

### Setup Scripts

```bash
./scripts/setup-android-libavif.sh   # Clone libavif + AOM into cpp/libavif/
./scripts/setup-ios-avif.sh          # Set up iOS deps (CocoaPods/SPM)
./scripts/prepare-for-publish.sh    # Runs both setup scripts + builds
./scripts/verify-integration.sh     # Verify integration works
```

### Version Catalog (`gradle/libs.versions.toml`)
| Key | Value |
|-----|-------|
| kotlin | 2.2.20 |
| agp | 8.11.2 |
| android-compileSdk | 36 |
| android-minSdk | 24 |
| composeMultiplatform | 1.9.1 |
| mavenPublish | 0.30.0 |

---

## 10. Dependencies

| Dependency | Scope | Purpose |
|---|---|---|
| `kotlinx-coroutines-core:1.8.0` | commonMain | Async operations |
| `filekit-core:0.12.0` | commonMain (api) | Cross-platform file handling |
| `kotlinx-io-core:0.8.2` | commonMain | FileKit I/O |
| `kotlinx-coroutines-android:1.8.0` | androidMain | Android dispatcher |
| `exifinterface:1.3.7` | androidMain | EXIF orientation reading |
| `avif.swift` (2.1.x) | iOS (SPM) | AVIF encode/decode via aom + dav1d |
| `libavif` (v1.2.1+) | Android (C++) | AVIF encode/decode via AOM |

---

## 11. SHARED MODULE AGENTS

### Files
- `shared/AGENTS.md` — Shared module instructions
- `shared/src/androidMain/cpp/AGENTS.md` — Android C++/JNI details

---

## 12. ANTI-PATTERNS & CRITICAL RULES

### NEVER Do These

1. **Global CMake linker flags** — `set(CMAKE_SHARED_LINKER_FLAGS "...")` for 16KB alignment breaks libavif/AOM. Always use `target_link_options(... PRIVATE ...)`.

2. **Type error suppression** — No `as Any` / `@Suppress` in Kotlin unless absolutely necessary (and documented). Never suppress type errors in the public API.

3. **Silent fallbacks** — v0.2.3+ removed JPEG fallback. Missing native deps throw explicit `AvifError.EncodingFailed` / `AvifError.DecodingFailed`.

4. **Commit `gradle.properties`** — Contains Maven/GPG credentials. Never commit.

5. **Speed ≥ 7 on iOS** — libaom's `AOM_USAGE_REALTIME` mode (speed ≥ 7) uses CBR rate control incompatible with quantizer-based still image encoding. Clamped to max 6 in `AVIFNativeConverter.swift`.

6. **Modify `init` block in EncodingOptions** — Contains `require()` validation. If adding options, add corresponding validation.

7. **Breaking expect/actual contract** — If you change `expect class AvifConverter` signature, you must update BOTH `androidMain` and `iosMain` actual classes, OR it won't compile on one platform.

### iOS SPM Quirks

- XCFramework URL + checksum in `Package.swift` must match the GitHub Release
- First-time SPM resolve requires clean build folder (Cmd+Shift+K)
- Swift classes in SPM modules register under two possible ObjC names — `"AvifKit.AvifKitSetup"` (module-prefixed) and `"AvifKitSetup"` (non-prefixed). The auto-reg code tries both.
- The ObjC auto-reg target is separate because SPM forbids mixing Swift and ObjC in the same target folder.

### Adaptive Compression Notes

- SMART uses binary search between quality 40-100, max 8 attempts
- STRICT uses progressive adjustments, max 10 attempts
- Both fall back to aggressive settings (quality=40, speed=10, maxDim=1024) if target not met
- `adjustCompressionParameters()` adjusts quality, maxDimension, subsample, alphaQuality, speed based on `reductionRatio = targetSize / currentSize`

---

## 13. KEY TECHNICAL DECISIONS

| Decision | Rationale |
|----------|-----------|
| Pre-built native binaries | Avoid requiring NDK/Xcode from library consumers |
| avif.swift over libavif-Xcode on iOS | Higher-level Swift API, simpler code, maintained by community |
| `__attribute__((constructor))` auto-reg | Zero setup for SPM consumers — no manual `registerNativeHandler()` call needed |
| Kotlin-side ObjC runtime discovery | Second line of defense if constructor hasn't run yet |
| `PlatformFileHelper` wrapper | Swift can't see Kotlin extension functions on typealiases |
| Static framework | Avoids runtime linking issues on iOS |
| Two compression strategies | SMART for quality-conscious users, STRICT for storage-critical scenarios |
| No silent JPEG fallback (v0.2.3+) | Clear errors better than silently producing different output |
| `@Throws(Exception::class)` on all public methods | Ensures Swift sees proper `NSError` instead of `_Nullable` return + crash |
