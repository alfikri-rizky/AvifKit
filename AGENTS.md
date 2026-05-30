# AVIFKIT PROJECT KNOWLEDGE BASE

**Generated:** 2026-02-16
**Commit:** c893f47
**Branch:** v0.1.4

## OVERVIEW

Kotlin Multiplatform (KMP) library for AVIF image encoding/decoding. Targets Android (JNI + libavif) and iOS (Swift + libavif). Publishes to Maven Central + SPM.

## STRUCTURE

```
AvifKit/
├── shared/                 # KMP library (THE PRODUCT - published as :avifkit)
│   │                       #   AGP 9 plugin: com.android.kotlin.multiplatform.library
│   ├── src/commonMain/     # Cross-platform Kotlin API
│   ├── src/androidMain/    # Android impl: Kotlin JNI bindings (external funs) + loadLibrary
│   └── src/iosMain/        # iOS impl (Swift bridge)
│       └── swift/          # AVIFNativeConverter.swift
├── shared-native/          # Android-only native build (published as :avifkit-native)
│   └── src/main/cpp/       #   Native C++ libavif/AOM + JNI wrapper, built via CMake/NDK.
│                           #   The KMP library plugin can't build CMake, so this is a plain
│                           #   com.android.library; :shared depends on it from androidMain and
│                           #   re-exports it transitively, so the .so reaches avifkit consumers.
├── composeApp/             # Android demo app — plain com.android.application (NOT published)
├── iosApp/                 # iOS demo app (NOT published)
├── scripts/                # Build automation
├── .github/workflows/      # CI/CD (Maven Central + iOS release)
└── Package.swift           # SPM distribution manifest
```

> **AGP 9 note:** AGP 9 forbids the Kotlin Multiplatform plugin in the same module as
> `com.android.library`/`com.android.application`, and the replacement KMP-library plugin
> can't build native code. Hence the native build was split into `:shared-native` and the
> Android demo (`:composeApp`) is a plain application module rather than a KMP module.
> Requires Gradle 9.4.1+ and JDK 17+.

## WHERE TO LOOK

| Task | Location | Notes |
|------|----------|-------|
| Add API methods | `shared/src/commonMain/.../AvifConverter.kt` | Expect class - add actual in platform dirs |
| Android native (C++) | `shared-native/src/main/cpp/avif_jni_wrapper.cpp` | JNI impl, built by `:shared-native` (com.android.library + CMake) |
| Android JNI bindings (Kotlin) | `shared/src/androidMain/.../AvifConverter.android.kt` | `external fun`s + `System.loadLibrary` |
| iOS native | `shared/src/iosMain/swift/AVIFNativeConverter.swift` | Swift bridge |
| Change version | `shared/build.gradle.kts` line 12 | Also update Package.swift checksum |
| Add dependency | `gradle/libs.versions.toml` | Version catalog |
| CI/CD | `.github/workflows/publish.yml` | Maven Central workflow |
| iOS release | `.github/workflows/publish-ios.yml` | XCFramework workflow |

## CODE MAP

| Symbol | Type | Location | Role |
|--------|------|----------|------|
| `AvifConverter` | expect class | commonMain | Main API (expect/actual) |
| `ImageInput` | sealed class | commonMain/Models.kt | Input types (bytes/bitmap/path/file) |
| `EncodingOptions` | data class | commonMain/Models.kt | Compression params |
| `Priority` | enum | commonMain/Models.kt | Presets (SPEED/QUALITY/STORAGE/BALANCED) |
| `CompressionStrategy` | enum | commonMain/Models.kt | SMART (quality) vs STRICT (size) |
| `PlatformBitmap` | typealias | platform-specific | Bitmap(Android) / UIImage(iOS) |
| `PlatformFile` | typealias | commonMain/FileKitExport.kt | FileKit wrapper |
| `AvifError` | sealed class | commonMain/AvifError.kt | Error types |

## CONVENTIONS

- **Expect/Actual Pattern**: API in commonMain, impl in androidMain/iosMain
- **No linters configured**: Follow `kotlin.code.style=official`
- **Java 11 target**: All Kotlin JVM code
- **C++17 standard**: Native code
- **iOS 13.0+ deployment**: Hardcoded in multiple places

## ANTI-PATTERNS (DO NOT)

- **NEVER commit `gradle.properties`** - contains Maven/GPG credentials
- **NEVER use global CMake linker flags** - breaks libavif/AOM memory layout
- **NEVER suppress type errors** - no `as any`, `@ts-ignore` equivalents
- **NEVER use libavif v0.3.7** - marked "DO NOT USE" in changelog
- **Fallback mode cannot decode AVIF** - only encode (outputs JPEG)

## CRITICAL: ANDROID 15+ COMPATIBILITY

16 KB page alignment is REQUIRED for Google Play (Android 15+):
```cmake
# CORRECT: Target-specific (in CMakeLists.txt)
target_link_options(avif-android-wrapper PRIVATE "-Wl,-z,max-page-size=16384")

# WRONG: Global flags (causes libavif memory issues)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
```

## ARCHITECTURE DECISIONS

1. **Two-tier Architecture**: Native libavif (default) with JPEG fallback
2. **Conditional Compilation**: `HAVE_LIBAVIF=1|0` in CMakeLists.txt
3. **Static iOS Framework**: Avoids runtime linking issues
4. **XCFramework Distribution**: Pre-built binaries via GitHub Releases
5. **FileKit Integration**: Cross-platform file API via typealias

## COMMANDS

```bash
# Build library
./gradlew :shared:build

# Run tests (KMP aggregate: Android host + iOS simulator)
./gradlew :shared:allTests

# Publish to Maven Central (library + its native companion; both share the version)
./gradlew :shared-native:publishToMavenCentral :shared:publishToMavenCentral

# Build iOS XCFramework
./gradlew :shared:assembleSharedReleaseXCFramework

# Setup Android libavif (for development)
./scripts/setup-android-libavif.sh

# Setup iOS libavif (for development)
./scripts/setup-ios-avif.sh
```

## PUBLISHING CHECKLIST

1. Update version in `shared/build.gradle.kts`
2. Build XCFramework: `./gradlew :shared:assembleSharedReleaseXCFramework`
3. Zip and upload to GitHub Release
4. Update `Package.swift` checksum (SHA256)
5. Push tag `v{version}` to trigger CI

## NOTES

- **Demo apps** (`composeApp`, `iosApp`) use local `projects.shared`, not published artifact
- **libavif** (Android): built by `:shared-native` into its own AAR (`avifkit-native`), which `:shared` pulls transitively so consumers of `avifkit` get the `.so` automatically. iOS: resolved via SPM dependency.
- **Compression strategies**: SMART = highest quality within limit, STRICT = smallest possible
- **EXIF orientation**: Handled automatically on both platforms
- **Memory safety**: OutOfMemory errors are caught and wrapped in AvifError
