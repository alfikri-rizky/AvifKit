# ANDROID NATIVE C++ - JNI WRAPPER

## OVERVIEW

JNI wrapper bridging Kotlin to libavif.

`HAVE_LIBAVIF` gates whether the codec is compiled in — it is **not** a fallback to another
format. With `HAVE_LIBAVIF=0` the encode and decode entry points log an error and return
`nullptr`, which surfaces in Kotlin as `AvifError.EncodingFailed` / `AvifError.DecodingFailed`.
Returning fabricated or JPEG bytes from a function named `nativeEncode` would ship garbage to
consumers who then write it to a `.avif` file, so the build fails loudly instead.

## STRUCTURE

```
cpp/
├── CMakeLists.txt              # Build config (CRITICAL - 16KB alignment)
├── avif_jni_wrapper.cpp        # The JNI implementation — there is only one
└── libavif/                    # libavif + ext/aom, cloned by the setup script (git-ignored)
```

## CONDITIONAL COMPILATION

CMakeLists.txt picks one based on whether the libavif source is actually present:

```cmake
add_compile_definitions(HAVE_LIBAVIF=1)   # source found — the real codec
add_compile_definitions(HAVE_LIBAVIF=0)   # source missing — every entry point errors out
```

## JNI METHODS

All four are on `com.alfikri.rizky.avifkit.AvifConverter`.

| Native Method | Purpose |
|---------------|---------|
| `nativeEncode(pixels, w, h, quality, alphaQuality, speed, subsample, lossless, hasAlpha)` | Encode RGBA to AVIF |
| `nativeDecode(avifData)` | Decode AVIF to `DecodedImage` |
| `nativeGetAvifInfo(data)` | Parse-only: width, height, alpha — no pixel decode, so it works below API 31 |
| `nativeGetVersion()` | libavif version string |

`nativeDecode` builds `DecodedImage` reflectively, so its primary constructor signature
(`([IIIII)V` — pixels, width, height, irotAngle, imirAxis) must stay in sync with `Models.kt`.

## CRITICAL: 16KB PAGE ALIGNMENT

```cmake
# CORRECT — per-target, applied only to our own .so
target_link_options(avif-android-wrapper PRIVATE "-Wl,-z,max-page-size=16384")

# WRONG — global flags leak into libavif/AOM and cause memory corruption.
# Left commented out at the top of CMakeLists.txt so nobody re-adds them.
set(CMAKE_SHARED_LINKER_FLAGS "... -Wl,-z,max-page-size=16384")
```

Required by Google Play for Android 15+. NDK r28 emits 16 KB-aligned libraries by default; the
explicit flag covers older toolchains.

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
# The wrapper logs under LOG_TAG "AvifJNI", not the Kotlin class name
adb logcat | grep AvifJNI

# A healthy encode looks like:
#   I AvifJNI: nativeEncode: 1920x1440, quality=75, alphaQuality=90, speed=6, ...
#   I AvifJNI: Successfully encoded AVIF: 1920x1440, output size=19807 bytes
# HAVE_LIBAVIF=0 looks like:
#   E AvifJNI: libavif not compiled into this build (HAVE_LIBAVIF=0) ...
```
