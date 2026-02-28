# ANDROID NATIVE C++ - JNI WRAPPER

## OVERVIEW

JNI wrapper bridging Kotlin to libavif. Conditional compilation supports both native AVIF and JPEG fallback modes.

## STRUCTURE

```
cpp/
├── CMakeLists.txt              # Build config (CRITICAL - 16KB alignment)
├── avif_jni_wrapper.cpp        # Active JNI implementation
├── avif_jni_wrapper_production.cpp  # Production variant
├── avif_jni_wrapper.placeholder.cpp # Fallback (no libavif)
└── libavif/                    # libavif submodule (cloned by setup script)
```

## CONDITIONAL COMPILATION

```cmake
# With libavif (default after setup)
add_compile_definitions(HAVE_LIBAVIF=1)

# Without libavif (fallback mode)
add_compile_definitions(HAVE_LIBAVIF=0)
```

## JNI METHODS

| Native Method | Purpose |
|---------------|---------|
| `nativeEncode(pixels, w, h, quality, speed, subsample)` | Encode RGBA to AVIF |
| `nativeDecode(avifData)` | Decode AVIF to DecodedImage |
| `nativeIsAvif(data)` | Check AVIF signature |
| `nativeGetVersion()` | Get libavif version string |

## CRITICAL: 16KB PAGE ALIGNMENT

```cmake
# CORRECT (line 98-103)
if(ANDROID)
    target_link_options(avif-android-wrapper PRIVATE
        "-Wl,-z,max-page-size=16384"
    )
endif()

# WRONG - causes memory corruption in libavif/AOM
set(CMAKE_SHARED_LINKER_FLAGS "... -Wl,-z,max-page-size=16384")
```

## LIBAVIF SETUP

```bash
# Run once for development
./scripts/setup-android-libavif.sh

# This clones libavif + AOM into cpp/libavif/
# CMake then builds it with AVIF_CODEC_AOM=LOCAL
```

## BUILD OPTIONS

| Option | Value | Reason |
|--------|-------|--------|
| `AVIF_CODEC_AOM` | LOCAL | Build AOM from source |
| `AVIF_LIBYUV` | OFF | Reduce dependencies |
| `BUILD_SHARED_LIBS` | OFF | Static linking |
| `CONFIG_PIC` | 1 | Position-independent code |

## PIXEL FORMAT

Kotlin passes RGBA (4 bytes/pixel):
```cpp
// pixels array: [R, G, B, A, R, G, B, A, ...]
// width * height * 4 bytes total
```

## ERROR HANDLING

- Return `nullptr` on failure (Kotlin catches as exception)
- Log errors via `__android_log_print(ANDROID_LOG_ERROR, ...)`
- OutOfMemory caught in Kotlin layer

## DEBUGGING

```bash
# Check if library loads
adb logcat | grep "AvifConverter"

# Look for "Native library loaded successfully" or error message
```
