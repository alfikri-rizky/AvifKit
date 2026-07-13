# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AvifKit is a production-ready Kotlin Multiplatform library (v0.3.0) for AVIF image encoding/decoding on Android and iOS. Published to Maven Central (`io.github.alfikri-rizky:avifkit`) and distributed via Swift Package Manager.

**Key architectural decision:** Both platforms call the **same libavif (v1.2.1) + AOM** codec directly from Kotlin — symmetric, with one Kotlin runtime and one code path:
- **Android**: libavif via JNI wrapper (C++), built in the `:shared-native` module, shipped as `avifkit-native` `.so`.
- **iOS**: libavif via **Kotlin/Native cinterop** (`shared/src/nativeInterop/cinterop/libavif.def`). Codec static libs (`libavif.a` + `libaom.a`) are built by `scripts/build-ios-libavif.sh` and linked into the `Shared` framework. No Swift bridge, no avif.swift, no handler registration (see `docs/IOS_CINTEROP_SOLUTION.md`).

Both platforms use pre-built native binaries (no source compilation by consumers).

## Build Commands

### Essential Development Commands

```bash
# Build entire project (Android + iOS)
./gradlew :shared:build

# Run tests
./gradlew :shared:test

# Android demo app
./gradlew :composeApp:assembleDebug

# iOS XCFramework for distribution
./gradlew :shared:assembleSharedReleaseXCFramework

# Local Maven for testing (avoids uploading to Maven Central)
./gradlew :shared:publishToMavenLocal
```

### Platform-Specific

```bash
# Build iOS XCFramework + calculate checksum
./gradlew :shared:assembleSharedReleaseXCFramework
swift package compute-checksum shared/build/XCFrameworks/release/Shared.xcframework.zip

# Build Android with specific ABI (for debugging)
./gradlew :shared:build -Pandroid.defaultConfig.ndk.abiFilters=arm64-v8a

# Xcode iOS app build (from project root)
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 15' build
```

### Setup Scripts (for development/publishing)

```bash
# Setup Android native dependencies (clones libavif + AOM)
./scripts/setup-android-libavif.sh

# Setup iOS dependencies
./scripts/setup-ios-avif.sh

# Prepare everything for release
./scripts/prepare-for-publish.sh

# Verify integration works
./scripts/verify-integration.sh

# Package XCFramework for release
./scripts/package-xcframework.sh <version>
```

## Architecture

### Expect/Actual Pattern

AvifKit follows strict KMP expect/actual conventions:

1. **commonMain** (`shared/src/commonMain/kotlin/`): Define `expect class` signatures
2. **androidMain** (`shared/src/androidMain/kotlin/`): `actual class` implementation (JNI-based, uses Dispatchers.IO)
3. **iosMain** (`shared/src/iosMain/kotlin/`): `actual class` implementation (Swift bridge, uses Dispatchers.Default)

**Critical rule**: If you change an `expect` method signature, you MUST update BOTH `androidMain` and `iosMain` actual implementations.

### Core Files

| File | Purpose |
|------|---------|
| `shared/src/commonMain/kotlin/com/alfikri/rizky/avifkit/AvifConverter.kt` | Public API (expect class) |
| `shared/src/commonMain/kotlin/com/alfikri/rizky/avifkit/Models.kt` | EncodingOptions, Priority, ImageInput, etc. |
| `shared/src/commonMain/kotlin/com/alfikri/rizky/avifkit/AvifError.kt` | Sealed error hierarchy |
| `shared/src/androidMain/kotlin/com/alfikri/rizky/avifkit/AvifConverter.android.kt` | Android actual |
| `shared-native/src/main/cpp/avif_jni_wrapper.cpp` | JNI wrapper for libavif (`:shared-native` module) |
| `shared-native/src/main/cpp/CMakeLists.txt` | Conditional build (HAVE_LIBAVIF=0|1) |
| `shared-native/src/main/cpp/libavif/` | libavif v1.2.1 source (+ `ext/aom`) from `setup-android-libavif.sh`; used by Android JNI build AND iOS cinterop |
| `shared/src/iosMain/kotlin/com/alfikri/rizky/avifkit/AvifConverter.ios.kt` | iOS actual — calls libavif via cinterop |
| `shared/src/nativeInterop/cinterop/libavif.def` | cinterop binding to libavif's C API |
| `scripts/build-ios-libavif.sh` | Builds `libavif.a` + `libaom.a` for the 3 iOS targets |

### iOS Native Bridge

iOS calls `libavif` directly via cinterop — the exact analog of the Android JNI path, with no Swift:

```
Kotlin/Native (AvifConverter.ios.kt)
  └─ import libavif.*            (cinterop binding; libavif.def)
       └─ avifEncoderCreate / avifImageRGBToYUV / avifEncoderWrite   (encode)
       └─ avifDecoderReadMemory* / avifImageYUVToRGB                 (decode)
            (* via avifDecoderSetIOMemory + avifDecoderParse + avifDecoderNextImage)
  └─ UIImage <-> RGBA8888 via CoreGraphics (CGBitmapContext)         (no Swift)
```

`libavif.a` + `libaom.a` are statically linked into `Shared.framework` via `linkerOpts` in `shared/build.gradle.kts`, so the codec is always present (`isAvifSupported()` returns `true` unconditionally). The cinterop is shared across iOS targets via `kotlin.mpp.enableCInteropCommonization=true`.

### Android Native (C++ / JNI)

- **Conditional compilation**: CMake flag `HAVE_LIBAVIF` (0 or 1)
- When `HAVE_LIBAVIF=0`: Returns mock data (placeholder mode)
- When `HAVE_LIBAVIF=1`: Full libavif integration
- **Pixel format**: Input RGBA byte array → output ARGB_8888 int array

### Version Locations

The Gradle version for BOTH `:shared` (avifkit) and `:shared-native` (avifkit-native) comes from
a single source: `gradle/libs.versions.toml` (`avifkit = "x.y.z"`). CI publishes override it with
the `VERSION_NAME` Gradle property derived from the release tag (the vanniktech plugin gives that
property precedence). The two artifacts must always ship as a matching version pair — the Kotlin
side calls the `.so` through a private JNI signature.

When bumping version, update ALL of these:
1. `gradle/libs.versions.toml` (`avifkit = "x.y.z"`)
2. `Package.swift` (URL + checksum — updated automatically by the iOS publish workflow)
3. `AvifKit.podspec` (`spec.version`)
4. `README.md` (installation snippets)

## Publishing Workflow

### Maven Central (Android + KMP)

Triggered by: GitHub Release or manual workflow dispatch

```bash
# CI workflow: .github/workflows/publish.yml
# After GitHub Actions completes:
1. Go to https://central.sonatype.com/
2. Login with credentials
3. Navigate to 'Deployments'
4. Find deployment and click 'Publish'
5. Wait ~15 minutes for sync
```

### iOS (SPM + XCFramework)

Triggered by: Git tag `vX.Y.Z`

```bash
# CI workflow: .github/workflows/publish-ios.yml
# Process:
1. Builds XCFramework
2. Calculates checksum
3. Updates Package.swift on main branch
4. Force-moves tag to updated commit
5. Creates GitHub Release with XCFramework attached
```

**Important**: The workflow automatically updates `Package.swift` checksum and moves the tag. Don't manually create releases for version tags.

## Critical Rules & Anti-Patterns

### NEVER Do These

1. **Re-introducing a Swift handler / `AvifKitIos` registry on iOS**: iOS calls libavif directly via cinterop (v0.3.0+). Do NOT add back a runtime-registered Swift handler — in a Compose Multiplatform consumer it splits into two `AvifKitIos` singletons (one in `ComposeApp.framework`, one in `Shared.xcframework`) and silently fails. See `docs/IOS_CINTEROP_SOLUTION.md`.

2. **Building iOS without the codec static libs**: The iOS framework links `libavif.a` + `libaom.a` from `shared/src/nativeInterop/libs/ios/<target>/` via `linkerOpts`. These are build outputs (git-ignored); run `scripts/build-ios-libavif.sh` before any iOS link/assemble task, or linking fails with undefined `_avif*` symbols.

3. **Global CMake linker flags (Android)**: Using `set(CMAKE_SHARED_LINKER_FLAGS "...")` for 16KB alignment breaks libavif/AOM. Always use `target_link_options(avif-android-wrapper PRIVATE ...)` (see `shared-native/src/main/cpp/CMakeLists.txt:101-106`).

4. **Break expect/actual contract**: Changing `expect class AvifConverter` signature without updating BOTH platform implementations causes compilation failure on one platform.

5. **Silent fallbacks**: v0.2.3+ removed JPEG fallback. Missing native deps throw explicit `AvifError.EncodingFailed` / `AvifError.DecodingFailed`.

6. **Type suppression in public API**: Never use `as Any` / `@Suppress` in public API without documentation and clear justification.

7. **Modify EncodingOptions `init` block**: Contains `require()` validation. If adding options, add corresponding validation.

8. **Commit gradle.properties**: Contains Maven/GPG credentials. Always in .gitignore.

### iOS SPM Quirks

- XCFramework URL + checksum in `Package.swift` must match GitHub Release exactly
- First-time SPM resolve requires clean build folder (Cmd+Shift+K in Xcode)
- The SPM `AvifKit` product now vends the self-contained `Shared` XCFramework directly (no Swift wrapper, no avif.swift). Consumers `import Shared` and use `AvifConverter()`.
- `AvifKitSetup.registerNativeHandler()` no longer exists — remove any such call from consumer `init()`.

### Android 15+ (16KB Page Alignment)

**Correct approach** (CMakeLists.txt:98-103):
```cmake
target_link_options(avif-android-wrapper PRIVATE "-Wl,-z,max-page-size=16384")
```

**WRONG approach** (breaks libavif/AOM):
```cmake
set(CMAKE_SHARED_LINKER_FLAGS "... -Wl,-z,max-page-size=16384")  # ❌ NEVER
```

## Compression Strategies

When `EncodingOptions.maxSize != null`, adaptive compression is triggered:

- **SMART** (default): Binary search quality 40-100, max 8 attempts, optimizes for quality
- **STRICT**: Progressive adjustments, max 10 attempts, optimizes for smallest size

Both fall back to aggressive settings (quality=40, speed=10, maxDim=1024) if target not met.

## Error Handling

- All public methods have `@Throws(Exception::class)` — errors propagate as `NSError` to Swift
- Native methods return `null` on failure → Kotlin wraps in `AvifError`
- `OutOfMemoryError` caught specifically → rethrown as `AvifError.OutOfMemory`

## Platform Bitmap Mapping

| commonMain | Android | iOS |
|---|---|---|
| `expect class PlatformBitmap` | `actual typealias PlatformBitmap = Bitmap` | `actual typealias PlatformBitmap = UIImage` |

## FileKit Integration

Cross-platform file abstraction via `io.github.vinceglb:filekit-core:0.12.0`:
- Android: wraps `java.io.File`
- iOS: wraps `NSURL`
- Re-exported as `typealias PlatformFile` from `FileKitExport.kt`
- `PlatformFileHelper` exposes `size()`, `path`, `name` to Swift (which can't see Kotlin extension functions)

## Additional Documentation

- `SKILL.md` — Comprehensive project documentation (architecture, API reference, conventions)
- `shared/AGENTS.md` — Shared module instructions
- `shared/src/androidMain/cpp/AGENTS.md` — Android C++/JNI details
- `README.md` — User-facing documentation with usage examples

## Key Dependencies

| Dependency | Version | Purpose |
|---|---|---|
| Kotlin | 2.2.20 | Language |
| AGP | 8.11.2 | Android Gradle Plugin |
| Compose Multiplatform | 1.9.1 | UI (demo apps) |
| kotlinx-coroutines-core | 1.8.0 | Async operations |
| filekit-core | 0.12.0 | Cross-platform file handling |
| androidx.exifinterface | 1.3.7 | EXIF orientation (Android) |
| libavif + AOM | v1.2.1 / aom v3.12.0 | AVIF encode/decode on BOTH platforms (JNI on Android, cinterop on iOS) |

## Testing

```bash
# Run all tests
./gradlew :shared:test

# Android instrumented tests (requires emulator/device)
./gradlew :shared:connectedAndroidTest

# iOS tests (via Xcode)
xcodebuild test -scheme AvifKit -destination 'platform=iOS Simulator,name=iPhone 15'
```

**Note**: Current test coverage is minimal. Tests are located in `shared/src/commonTest/`.
