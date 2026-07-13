# AvifKit — Code Review: Bugs & Improvements

**Date:** 2026-07-11 · **Reviewed at:** `main` @ `c5a659b` (v0.3.1)
**Scope:** commonMain / androidMain / iosMain Kotlin, JNI wrapper (C++), CMake, Gradle build, cinterop, publish workflows, podspec, Package.swift, published Maven Central artifacts.

---

## Summary

| # | Severity | Finding | Area |
|---|----------|---------|------|
| C1 | 🔴 Critical | ✅ **FIXED 2026-07-11** — iOS alpha channel corrupted on encode **and** decode (premultiplied vs straight mismatch) | iOS codec |
| C2 | 🔴 Critical | ✅ **FIXED 2026-07-11** (`preserveMetadata` documented as no-op, not implemented) — `EncodingOptions` contract violated: `alphaQuality`, `lossless`, `preserveMetadata` silently ignored | API correctness |
| C3 | 🔴 Critical | ✅ **FIXED 2026-07-11** — `lossless = true` does not produce lossless AVIF even on iOS | API correctness |
| H1 | 🟠 High | ✅ **FIXED 2026-07-13** — `maxSize` silently no-ops (returns oversized file) when input is already AVIF | Compression |
| H2 | 🟠 High | ✅ **FIXED 2026-07-13** — Android `isAvifSupported()` returns `true` even when the native library failed to load | API correctness |
| H3 | 🟠 High | ✅ **FIXED 2026-07-13** — AVIF signature detection misses valid AVIF files (`avis`, `mif1`+compatible brands) | Format detection |
| H4 | 🟠 High | ✅ **FIXED 2026-07-13** — Decoder ignores AVIF orientation metadata (`irot`/`imir`, EXIF) on both platforms | Codec |
| H5 | 🟠 High | ✅ **FIXED 2026-07-13** — Version management fragile — junk `v0.3.0` on Maven Central, `0.3.0` never published, module versions drift (`:shared-native` says 0.2.10) | Release engineering |
| H6 | 🟠 High | ✅ **FIXED 2026-07-13** — CocoaPods podspec 404s — tag and download URL missing the `v` prefix | Distribution |
| H7 | 🟠 High | ✅ **FIXED 2026-07-13** — Placeholder codec path ships mock data if build is misconfigured (violates own no-silent-fallback rule) | Build integrity |
| H8 | 🟠 High | ✅ **FIXED 2026-07-13** — Effectively zero test coverage of codec paths + no CI on push/PR | Quality infra |
| M1 | 🟡 Medium | ✅ **FIXED 2026-07-13** — Hardware bitmaps (Coil/Glide) fail to encode on Android | Android |
| M2 | 🟡 Medium | ✅ **FIXED 2026-07-13** — `getImageInfo` misreports: `hasAlpha=true` for JPEGs (Android), fails on AVIF pre-API 31 / iOS 15 | API correctness |
| M3 | 🟡 Medium | ✅ **FIXED 2026-07-13** — `isAvifFile` reads whole file for a 12-byte check and uses `runBlocking` (ANR risk) | Performance |
| M4 | 🟡 Medium | ✅ **FIXED 2026-07-13** — `FromPath` AVIF passthrough decided by file extension, not content | Consistency |
| M5 | 🟡 Medium | ✅ **FIXED 2026-07-13** — Adaptive compression re-decodes source image on every attempt (up to 10×) and is not cancellable | Performance |
| M6 | 🟡 Medium | ✅ **FIXED 2026-07-13** — Alpha plane always encoded, even for opaque images → larger files | Output size |
| M7 | 🟡 Medium | ✅ **FIXED 2026-07-13** — JNI hardening: OOB debug read for <16-byte input, uncaught `std::bad_alloc` aborts app | Native robustness |
| M8 | 🟡 Medium | ✅ **FIXED 2026-07-13** — Resize edge cases: 0-dimension crash (Android), points-vs-pixels mismatch + deprecated API (iOS) | Codec |
| L1 | 🟢 Low | ✅ **FIXED 2026-07-13** — Empty `ByteArray` input crashes with non-`AvifError` exception on iOS | Edge case |
| L2 | 🟢 Low | ✅ **FIXED 2026-07-13** — CPU-bound encoding runs on `Dispatchers.IO` (Android) | Performance |
| L3 | 🟢 Low | ✅ **FIXED 2026-07-13** — `maxThreads = 4` hardcoded on both platforms | Performance |
| L4 | 🟢 Low | ✅ **FIXED 2026-07-13** — `AvifSamples.kt` ships inside the published library | Artifact hygiene |
| L5 | 🟢 Low | ✅ **FIXED 2026-07-13** — Documentation drift (CLAUDE.md deps, README links, stale comments) | Docs |
| L6 | 🟢 Low | ✅ **FIXED 2026-07-13** — Repo hygiene: stale `shared/src/androidMain/cpp/` leftover, local main behind origin, stale local tags | Housekeeping |
| L7 | 🟢 Low | ✅ **FIXED 2026-07-13** — `c++_shared` STL for a single tiny `.so`; minSdk 24 vs `ANDROID_PLATFORM=android-21` mismatch | Build config |
| L8 | 🟢 Low | ✅ **FIXED 2026-07-13** — Weak magic-byte detection for WEBP; BMP/GIF/HEIF only detected by extension | Format detection |
| L9 | 🟢 Low | ✅ **FIXED 2026-07-13** — Tag force-move during iOS release can break SPM resolves in flight | Release process |

---

## 🔴 Critical

> **Status update (2026-07-11):** All three Critical findings were fixed on the working tree.
> - **C1**: `rgb.alphaPremultiplied = AVIF_TRUE` set on both the encode path (`encodeRgbaToAvif`) and decode path (`decodeAvifToImage`) so libavif converts between CoreGraphics' premultiplied buffers and AVIF's straight alpha.
> - **C2**: JNI signature extended to `nativeEncode(pixels, w, h, quality, alphaQuality, speed, subsample, lossless)`; `qualityAlpha` and lossless now wired through on Android. `preserveMetadata` is documented as a no-op in `Models.kt` and removed from the `Priority.QUALITY` preset (implementing it is future work — naive EXIF copy would double-apply the already-baked-in orientation).
> - **C3**: Both platforms now force YUV444 + identity matrix coefficients + `qualityAlpha=100` when `lossless=true`.
>
> Verified: 3 new regression tests in `AvifConverterIosRoundTripTest` (encode-direction alpha, decode-direction alpha via raw-libavif straight-alpha ground truth, and a pixel-exact lossless round-trip) — all pass on the iOS simulator, and were confirmed to FAIL against the pre-fix code. Android: Kotlin + full native `.so` build green; JNI wrapper syntax-checked for both `HAVE_LIBAVIF` modes. Android codec behavior is not covered by automated tests yet (needs instrumented tests, see H8) — the C++ changes mirror the iOS logic exactly.

### C1. iOS alpha channel is corrupted on encode and decode

**Files:**
- Encode: `shared/src/iosMain/kotlin/com/alfikri/rizky/avifkit/AvifConverter.ios.kt:633` (`uiImageToRgba` uses `kCGImageAlphaPremultipliedLast`) and `:502-514` (`encodeRgbaToAvif` never sets `rgb.alphaPremultiplied`)
- Decode: `AvifConverter.ios.kt:570-586` (libavif outputs straight alpha) and `:680` (`rgbaToUIImage` claims `kCGImageAlphaPremultipliedLast`)

**The bug:** CoreGraphics can only produce/consume **premultiplied** RGBA, but `avifRGBImageSetDefaults` sets `alphaPremultiplied = AVIF_FALSE` (straight alpha).

- **Encode:** premultiplied bytes from the CG context are fed to libavif, which believes they are straight alpha. Every pixel with `alpha < 255` is encoded with darkened RGB values.
- **Decode:** libavif emits straight-alpha RGBA, but the CGImage is created claiming premultiplied. Semi-transparent pixels render wrong (and can be mathematically invalid where `color > alpha`).

Fully opaque images (JPEG sources) are unaffected, which is why the round-trip test passes — it uses `A = 0xFF` only.

**Failure scenario:** Convert any PNG with a soft shadow / anti-aliased logo on iOS → halo artifacts and darkened edges; decode any AVIF with transparency → washed-out or fringed colors. Android is correct (`Bitmap.getPixels` returns unpremultiplied), so the two platforms produce visibly different output for the same input.

**Action plan:**
1. In `encodeRgbaToAvif`, set `rgb.alphaPremultiplied = AVIF_TRUE` after `avifRGBImageSetDefaults` (libavif will unpremultiply during RGB→YUV).
2. In `decodeAvifToImage`, set `rgb.alphaPremultiplied = AVIF_TRUE` before `avifImageYUVToRGB` so libavif outputs premultiplied data matching the CG bitmap info.
3. Extend `AvifConverterIosRoundTripTest` with a semi-transparent test image (e.g., `A = 0x80`) asserting channel values round-trip within codec tolerance.

---

### C2. `EncodingOptions` contract violated: options silently ignored

**Files:**
- `shared/src/androidMain/kotlin/com/alfikri/rizky/avifkit/AvifConverter.android.kt:23-30, 486-493` — `nativeEncode` only receives `quality`, `speed`, `subsample`
- `shared-native/src/main/cpp/avif_jni_wrapper.cpp:72` — `encoder->qualityAlpha = quality` (hardcoded)
- `preserveMetadata` — never read anywhere on either platform

**The bug:** The public API advertises options that do nothing:

| Option | Android | iOS |
|---|---|---|
| `alphaQuality` | ❌ ignored (alpha uses `quality`) | ✅ applied |
| `lossless` | ❌ ignored entirely | ⚠️ applied but not actually lossless (see C3) |
| `preserveMetadata` | ❌ no-op | ❌ no-op (EXIF/XMP/ICC never copied to output) |

`Priority.QUALITY` even sets `preserveMetadata = true`, promising something the library never does. The same `EncodingOptions` produce different files per platform, breaking the core KMP symmetry promise.

**Action plan:**
1. Extend the JNI signature: `nativeEncode(pixels, width, height, quality, alphaQuality, speed, subsample, lossless)`; wire `qualityAlpha` and lossless handling in `avif_jni_wrapper.cpp`; update the Kotlin `external fun` (keep expect/actual parity per CLAUDE.md rule 4).
2. Implement `preserveMetadata`: extract EXIF from source (Android `ExifInterface` / iOS `CGImageSource`), attach via `avifImageSetMetadataExif`; **or** remove the option with a deprecation note and drop it from `Priority.QUALITY`. Shipping an ignored option is worse than not having it.
3. Add a parity test matrix (same input + options on both platforms, compare structural properties of output).

---

### C3. `lossless = true` does not produce lossless output

**File:** `shared/src/iosMain/kotlin/com/alfikri/rizky/avifkit/AvifConverter.ios.kt:491`

**The bug:** Setting `encoder.quality = AVIF_QUALITY_LOSSLESS` (100) is necessary but not sufficient. True lossless AVIF requires:
- `matrixCoefficients = AVIF_MATRIX_COEFFICIENTS_IDENTITY` (otherwise RGB→YUV rounding loses data),
- `AVIF_PIXEL_FORMAT_YUV444` (chroma subsampling is inherently lossy — the default here is YUV420),
- full range, `qualityAlpha = 100`.

Today `EncodingOptions(lossless = true)` produces a **lossy** file on iOS and a plain quality-75 file on Android (C2).

**Action plan:**
1. On both platforms: when `lossless` is set, force YUV444 + identity matrix coefficients + full range + `qualityAlpha = 100`, overriding `subsample`.
2. Document in `Models.kt` that `lossless` overrides `subsample`/`quality`/`alphaQuality`.
3. Add a round-trip test: encode lossless, decode, assert pixel-exact equality.

---

## 🟠 High

> **Status update (2026-07-13):** All eight High findings were fixed.
> - **H1**: adaptive compression now short-circuits fitting AVIF input and decodes + re-encodes oversized AVIF input; the best-effort contract is documented on `EncodingOptions.maxSize`.
> - **H2**: `isAvifSupported()` returns real availability (library loaded AND compiled against libavif, via the version string).
> - **H3**: one shared `ftyp` parser (`commonMain/AvifFormat.kt`) checks major + compatible brands (`avif`/`avis`) on both platforms; header reads widened from 12 to 64 bytes; dead `nativeIsAvif` removed.
> - **H4**: decode applies `irot`/`imir` via shared `RgbaTransform` (commonMain) — the JNI wrapper passes the properties through `DecodedImage`, iOS reads them from the cinterop struct.
> - **H5**: single version source in `gradle/libs.versions.toml` for both modules (CI `VERSION_NAME` still overrides); publish.yml normalizes + validates the version (rejects `vX.Y.Z` style junk); CLAUDE.md checklist updated.
> - **H6**: podspec tag and download URL carry the `v` prefix (plus `curl -f` so a 404 fails); `pod spec lint --quick` passes.
> - **H7**: CMake now hard-fails without libavif unless `-DAVIFKIT_ALLOW_PLACEHOLDER=ON`; placeholder branches return null (→ `AvifError`) instead of mock data.
> - **H8**: `.github/workflows/ci.yml` (build + host/simulator tests on macOS, instrumented emulator job on Ubuntu); commonTest suites for AvifFormat/RgbaTransform/EncodingOptions; `androidDeviceTest` round-trip suite mirroring iOS; the KGP simulator-test task self-disable is fixed (`device` pinned + `enabled = true` in shared/build.gradle.kts), so `allTests` actually runs simulator tests now.
>
> Verified: 29 tests green via `:shared:iosSimulatorArm64Test` (incl. new irot + maxSize regression tests), 6 instrumented tests green on an API 35 emulator via `:shared:connectedAndroidDeviceTest` (incl. on-device alpha + lossless + maxSize), `:shared:build` + `ktfmtCheck` green, podspec lints clean.

### H1. `maxSize` silently returns oversized output for AVIF input

**Files:** `AvifConverter.android.kt:410-413` and `AvifConverter.ios.kt:399-407` (`convertStandard` passthrough), used by `convertWithSmartCompression` / `convertWithStrictCompression`

**The bug:** `convertStandard` returns already-AVIF input bytes untouched, ignoring all options. When `maxSize` is set, the adaptive loops call `convertStandard` up to 8–10 times, get the identical passthrough bytes every time, burn the attempts, run the "aggressive fallback" (also a passthrough), and return the original file — **larger than `maxSize`, with no error**.

**Failure scenario:** `encodeAvif(ImageInput.from(threeMbAvif), options = EncodingOptions(maxSize = 500_000))` → returns the 3 MB input after 9 wasted iterations.

**Action plan:**
1. In `convertStandard`, when input is AVIF **and** re-encode is required (maxSize set, or explicit options), decode it first and re-encode through the normal path.
2. Short-circuit the passthrough check before the adaptive loop, not inside it (also fixes the 8–10 redundant iterations).
3. Decide and document the contract when the target cannot be met (currently the fallback can also miss the target silently): either throw `AvifError.EncodingFailed("could not meet maxSize")` or return a result object carrying the achieved size.

### H2. Android `isAvifSupported()` lies when the native library is missing

**File:** `AvifConverter.android.kt:152-156`

Returns hardcoded `true` (comment admits "Currently returns true with placeholder implementation") while the class tracks `nativeLibraryLoaded`. A consumer that feature-detects before converting will proceed and then get `EncodingFailed`.

**Action plan:** `return nativeLibraryLoaded` (optionally also probe `nativeGetVersion().startsWith("libavif")` to catch placeholder builds — see H7). One-line fix plus a unit test.

### H3. AVIF signature detection misses valid AVIF files

**Files:** `AvifConverter.android.kt:597-603`, `AvifConverter.ios.kt:733-738`, `avif_jni_wrapper.cpp:468-471`

**The bug:** All three implementations only match bytes 4–11 == `ftypavif`, i.e. major brand `avif`. Misses:
- `avis` major brand (animated AVIF),
- files with major brand `mif1`/`miaf` and `avif` in the *compatible brands* list (produced by several encoders — ironically, the project's own mock header at `avif_jni_wrapper.cpp:174-182` is this kind of file, and fails its own Kotlin check),
- `data.size > 12` should be `>= 12` (off-by-one; a 12-byte header is valid to test).

**Failure scenario:** `isAvifFile()` returns false for a valid AVIF → `convertStandard` treats it as a regular image → `BitmapFactory` can't decode AVIF below API 31 / `UIImage` below iOS 16 → `DecodingFailed` for a file the library itself could decode.

**Action plan:**
1. Parse the `ftyp` box properly: read box size, verify `ftyp`, check major brand ∈ {`avif`, `avis`} then scan compatible brands (bytes 16..boxSize) for them. ~20 lines, put it in `commonMain` so both platforms share one implementation (it's pure byte logic — the current triplication is also a maintenance smell).
2. Alternative: expose libavif's `avifPeekCompatibleFileType` through JNI/cinterop.
3. Unit-test with fixtures: `ftypavif`, `ftypavis`, `ftypmif1...avif`, truncated files.

### H4. Decoded images lose orientation (irot/imir/EXIF ignored)

**Files:** `avif_jni_wrapper.cpp:249` (sets `ignoreExif = AVIF_FALSE` with comment "IMPORTANT: Preserve EXIF for orientation data" — but never reads `decoder->image->exif` or `transformFlags`), `AvifConverter.ios.kt:548-549` (same pattern)

**The bug:** AVIF stores orientation as `irot` (rotation) / `imir` (mirror) transform properties. libavif exposes them via `image->transformFlags`, `image->irot`, `image->imir` but does **not** apply them to pixels. Neither platform applies them, so AVIF files with orientation metadata (e.g., converted iPhone photos) decode sideways/mirrored. Encoding is fine (orientation gets baked in before encode); this is decode-only.

**Action plan:**
1. After `avifDecoderNextImage`, read `transformFlags`; if `AVIF_TRANSFORM_IROT`/`AVIF_TRANSFORM_IMIR` present, rotate/mirror the RGBA buffer (Android: apply a `Matrix` to the produced Bitmap — the helper already exists; iOS: apply `UIImageOrientation` when creating the UIImage — cheapest correct fix).
2. Add decode-orientation fixtures (an AVIF with `irot=1`) to tests on both platforms.

### H5. Version management is fragile — already produced junk releases

**Evidence (Maven Central metadata, verified during review):**
- `io.github.alfikri-rizky:avifkit` versions include a junk **`v0.3.0`** (with prefix), and **`0.3.0` was never published** — README instructions for 0.3.0 users pointed to a nonexistent Maven version until 0.3.1.
- `:shared-native/build.gradle.kts` hardcodes `version = "0.2.10"` while `:shared` says `0.3.1`. CI works only because the vanniktech plugin prefers the `VERSION_NAME` Gradle property written by `publish.yml` — the hardcoded values are dead in CI but **live for `publishToMavenLocal`**, which produces a mismatched `avifkit:0.3.1` + `avifkit-native:0.2.10` pair locally.
- CLAUDE.md's "Version Locations" checklist omits `shared-native/build.gradle.kts` and `README.md`.
- The junk `v0.3.0` shows the version input path has no validation (`workflow_dispatch` input taken verbatim; Maven Central is immutable, so it's permanent).

**Action plan:**
1. Single source of truth: put `VERSION_NAME=x.y.z` in a checked-in root `gradle.properties` (non-secret part) or root `build.gradle.kts`, and have both modules read it (`version = providers.gradleProperty("VERSION_NAME").get()`); delete hardcoded versions.
2. In `publish.yml`'s "Determine version" step, normalize and validate: strip a leading `v`, then `[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || exit 1`.
3. Add a CI guard asserting the Gradle version matches the tag on release events.
4. Update the CLAUDE.md checklist (add `shared-native`, README, and note CI's `VERSION_NAME` precedence).

### H6. CocoaPods distribution is broken (404)

**File:** `AvifKit.podspec:29, 38-43`

- `:tag => "#{spec.version}"` → tag `0.3.1`, but git tags are `v0.3.1`.
- `prepare_command` downloads `releases/download/0.3.1/Shared.xcframework.zip`, but the real asset lives under `releases/download/v0.3.1/` → curl fetches a 404 page, `unzip` fails, `pod install` errors.

README advertises `pod 'AvifKit', '~> 0.3.1'`, so any CocoaPods consumer is broken today.

**Action plan:** Change both to `"v#{spec.version}"`, run `pod spec lint AvifKit.podspec` to verify, and add lint to CI (or remove the CocoaPods section from the README if unsupported — half-supporting is the worst option).

### H7. Placeholder codec path can silently ship garbage

**Files:** `avif_jni_wrapper.cpp:164-191` (mock encode returns a fake 28-byte AVIF header), `:391-448` (mock decode returns a 100×100 gradient), `shared-native/src/main/cpp/CMakeLists.txt:61-67` (only warns when libavif missing)

**The bug:** The project's own rule (CLAUDE.md #5, README "No Fallback Mode v0.2.3+") says missing native deps must throw. But if `setup-android-libavif.sh` is skipped, CMake happily builds `HAVE_LIBAVIF=0` and the shipped `.so` returns **fake data** — encode "succeeds" with a garbage header, decode returns a gradient. Combined with H2 (`isAvifSupported() == true`) nothing at any layer surfaces the misconfiguration.

**Action plan:**
1. CMake: `message(FATAL_ERROR ...)` when libavif is missing unless `-DAVIFKIT_ALLOW_PLACEHOLDER=ON` is explicitly passed (keep the dev escape hatch, make the default safe).
2. Placeholder branches should return `nullptr` (→ Kotlin throws `AvifError`) instead of mock bytes.
3. Optional belt-and-braces: CI step that runs `nm` on the built `.so` asserting `avifEncoderWrite` is bound (the iOS script already does exactly this — mirror it).

### H8. No meaningful tests, no CI on push/PR

**Evidence:** `SharedCommonTest.kt` asserts `1 + 2 == 3`. The only real test is the iOS round-trip (opaque image only). `.github/workflows/` contains only the two publish workflows — nothing builds or tests PRs/pushes, so a red build is only discovered at release time (and the historical changelog — v0.2.6→v0.2.10 shipping four consecutive broken iOS releases — is exactly the failure mode tests+CI prevent).

**Action plan (incremental):**
1. Add `.github/workflows/ci.yml`: on push/PR → `./gradlew :shared:build :shared:allTests` (reuse the setup steps from `publish.yml`; cache the libavif source and iOS static libs by libavif tag to keep it fast).
2. Port the iOS round-trip test to Android as an instrumented test (`connectedAndroidTest` on a CI emulator) — the encode/decode path is device-only, Robolectric won't load the `.so`.
3. Grow the matrix along the bugs in this review: alpha round-trip (C1), options parity (C2), lossless (C3), maxSize behavior incl. AVIF input (H1), brand detection fixtures (H3), orientation fixtures (H4).
4. `commonTest`: real tests for the pure-Kotlin surface (EncodingOptions validation, `fromPriority` mapping, adaptive parameter adjustment logic — extract `adjustCompressionParameters` to commonMain first, see M-note below).

---

## 🟡 Medium

> **Status update (2026-07-13):** All eight Medium findings were fixed.
> - **M5** (backbone): the duplicated SMART/STRICT/adjust/fallback logic now lives once in `commonMain/AdaptiveCompression.kt`, is cancellation-aware (`ensureActive()` per attempt), terminates STRICT early at a parameter fixed point, and — crucially — the platform decodes the source ONCE and the loop only re-encodes it. Unit-tested in `AdaptiveCompressionTest` (commonTest).
> - **M1**: `ensureReadableBitmap` copies HARDWARE bitmaps to ARGB_8888 before `getPixels()`.
> - **M2**: `getImageInfo` routes AVIF through a parse-only path (`nativeGetAvifInfo` on Android, `avifDecoderParse` on iOS) — exact dimensions/alpha, and works below API 31 / on iOS 15; non-AVIF alpha no longer comes from BitmapFactory's decode config (which reported alpha for every JPEG).
> - **M3/M4**: header reads are bounded to `AvifFormat.HEADER_CHECK_SIZE`, and AVIF is detected by content on every input kind (FromPath no longer trusts the `.avif` extension).
> - **M6**: opaque sources skip the alpha plane entirely (`rgb.ignoreAlpha` + YUV-only planes) — smaller files, no phantom alpha on decode.
> - **M7**: 16-byte debug log guarded, the decode pixel `std::vector` guarded against `std::bad_alloc` (→ catchable `AvifError`, not `std::terminate`), and the `(a << 24)` ARGB pack computed in `uint32_t` to avoid signed-overflow UB.
> - **M8**: resize clamps both sides to ≥ 1px (no 0-dimension crash); iOS now scales by CGImage PIXEL dimensions (not points) and renders via `UIGraphicsImageRenderer` (the deprecated `UIGraphicsBeginImageContext*` is gone).
>
> Verified: 37 tests green via `:shared:iosSimulatorArm64Test` (adds opaque-alpha, AVIF getImageInfo, pixel-resize, plus the AdaptiveCompression unit tests), and 10 instrumented tests green on an API 35 emulator via `:shared:connectedAndroidDeviceTest` (adds hardware-bitmap, opaque-alpha, AVIF getImageInfo, extreme-aspect-ratio). Full `:shared:build` + `ktfmtCheck` green; JNI wrapper syntax-checked in both HAVE_LIBAVIF modes.

### M1. Hardware bitmaps fail to encode (Android)

**File:** `AvifConverter.android.kt:567-569`

`bitmap.getPixels()` throws `IllegalStateException` for `Config.HARDWARE` bitmaps — which is what Coil/Glide return by default on API 26+. The error surfaces as a generic `EncodingFailed` instead of just working.

**Action:** in `encodeBitmapToAvif`, if `bitmap.config == Bitmap.Config.HARDWARE` (or `bitmap.isMutable == false && config == null`), `bitmap.copy(Bitmap.Config.ARGB_8888, false)` first. Test with a hardware bitmap fixture.

### M2. `getImageInfo` misreports format facts

**Files:** `AvifConverter.android.kt:175-234`, `AvifConverter.ios.kt:151-223`

- **Android `hasAlpha`:** `options.outConfig == ARGB_8888` is true for JPEGs too (default decode config) → `hasAlpha = true` for every JPEG. Wrong signal.
- **Android AVIF input, API < 31:** `BitmapFactory` can't decode AVIF → `outWidth/outHeight = -1` returned as real dimensions, no error.
- **iOS AVIF input, iOS 15:** `UIImage.imageWithData` returns nil for AVIF (UIKit AVIF support is iOS 16+) → throws `InvalidInput` for a file the library can decode; also fully decodes the image just to read dimensions.

**Action:** route AVIF inputs through libavif (`avifDecoderParse` only — no pixel decode; gives width/height/depth/alpha cheaply) on both platforms; on iOS use `CGImageSourceCopyPropertiesAtIndex` for non-AVIF instead of a full `UIImage` decode; derive `hasAlpha` from the format properties, not decode config.

### M3. `isAvifFile` reads entire file, can block main thread

**Files:** `AvifConverter.android.kt:158-173`, `AvifConverter.ios.kt:129-149`

`FromPath` does `File(path).readBytes().take(12)` — a 50 MB read for a 12-byte check (iOS `dataWithContentsOfURL` likewise). `FromFile` wraps `readBytes()` in `runBlocking` inside a non-suspend function — calling it from the main thread with a large file is an ANR risk.

**Action:** read only the first 16 bytes (`FileInputStream`/`RandomAccessFile` on Android, `NSFileHandle`/`InputStream` on iOS). For `FromFile`, either accept the same header-read approach via FileKit path, or add a `suspend` overload and deprecate the blocking behavior for that input type.

### M4. `FromPath` passthrough decided by extension, not content

**Files:** `AvifConverter.android.kt:433-435`, `AvifConverter.ios.kt:417-418`

`FromBytes`/`FromFile` sniff magic bytes; `FromPath` checks `.avif` extension. A mislabeled `.avif` (actually JPEG) is passed through unconverted; a real AVIF named `.bin` is sent to `BitmapFactory`/`UIImage` and fails on older OS versions.

**Action:** read the header and reuse the (fixed, common — see H3) signature check for all input kinds.

### M5. Adaptive compression re-decodes the source on every attempt; not cancellable

**Files:** `convertWithSmartCompression` / `convertWithStrictCompression` on both platforms

Each attempt calls `convertStandard(input, …)` which re-reads the file, re-decodes it, and re-applies EXIF rotation — up to 8 (SMART) or always 10 (STRICT) times. STRICT also always burns all 10 attempts even when attempt 1 already meets the target and later attempts can't improve (parameters floor out). None of the loops check coroutine cancellation, and each native encode can take seconds.

**Action:**
1. Decode/orient once before the loop; loop only over `encodeBitmapToAvif`/`encodeImageToAvif` with the reused bitmap.
2. Add `coroutineContext.ensureActive()` at the top of each attempt.
3. STRICT: break out early when parameter adjustment reaches a fixed point (options stop changing).
4. Note: the SMART/STRICT/adjustment logic is duplicated ~line-for-line in both actuals (~300 lines). Extract it into `commonMain` (strategy operates on a `suspend (EncodingOptions) -> Sized` encode function) — halves the surface where bugs like H1 must be fixed twice.

### M6. Alpha plane always encoded, even for opaque images

**Files:** `AvifConverter.ios.kt:497`, `avif_jni_wrapper.cpp:96` (`AVIF_PLANES_YUV | AVIF_PLANES_A` unconditionally, RGBA format always)

Opaque JPEG sources still get a full alpha plane encoded → measurably larger files and `hasAlpha=true` on every decode.

**Action:** plumb a `hasAlpha` flag (Android `bitmap.hasAlpha()`, iOS `imageHasAlpha`, both already exist) down to the encoder; when opaque, allocate only YUV planes and skip the alpha plane (libavif then omits the alpha item).

### M7. JNI hardening

**File:** `avif_jni_wrapper.cpp`

- `:232-236` — logs "first 16 bytes" of input without checking `dataLength >= 16`: heap out-of-bounds read for tiny inputs.
- `:323` — `std::vector<int32_t> pixels(width * height)` can throw `std::bad_alloc` (up to ~1 GB for max-size images); an exception escaping a JNI entry point calls `std::terminate` → app abort instead of a catchable `AvifError`.
- `:333` — `(a << 24)` on an int-promoted `uint8_t` is signed-overflow territory; cast through `uint32_t`.

**Action:** guard the debug log with the length; wrap encode/decode bodies in `try { … } catch (...) { return nullptr; }`; use `static_cast<uint32_t>(a) << 24`.

### M8. Resize edge cases

- **Android** `AvifConverter.android.kt:590-594`: extreme aspect ratios (e.g. 20000×1 panorama line, `maxDimension=1024`) produce `newHeight = 0` → `createScaledBitmap` throws. Fix: `coerceAtLeast(1)` on both dimensions.
- **iOS** `AvifConverter.ios.kt:696-731`: compares `image.size` (points) against `maxDimension` (intended as pixels — Android uses pixels), so `scale > 1` images (screenshots, asset catalogs) dodge the resize; `UIGraphicsBeginImageContextWithOptions` is deprecated since iOS 17. Fix: compute from `CGImageGetWidth/Height` and render with `UIGraphicsImageRenderer` (or a plain `CGBitmapContext`, consistent with `uiImageToRgba`), and clamp to ≥ 1 px.

---

## 🟢 Low

> **Status update (2026-07-13):** All nine Low findings were fixed.
> - **L1**: iOS `toNSData`/`toByteArray` short-circuit empty input instead of throwing `ArrayIndexOutOfBoundsException`.
> - **L2**: Android orchestration runs on `Dispatchers.Default` (CPU-bound codec work); raw `java.io.File` reads/writes are wrapped in `Dispatchers.IO` via `readFileOnIo`.
> - **L3**: codec `maxThreads` comes from the CPU count — `sysconf(_SC_NPROCESSORS_ONLN)` (Android, capped 1..8) / `NSProcessInfo.activeProcessorCount` (iOS, capped 1..8).
> - **L4**: `AvifSamples.kt` deleted from the published library.
> - **L5**: doc sweep — CLAUDE.md dependency versions (AGP 9.2.1, Compose 1.9.3, coroutines 1.11.0, exifinterface 1.4.2) and the `AGENTS.md` path, the `:shared-native` "JPEG fallback" comment, the README v0.2.3 link (→ `/releases/latest`), and the publish-ios release-note wording.
> - **L6**: removed the `shared/src/androidMain/cpp` leftover and the root `Shared.xcframework.zip`.
> - **L7**: `ANDROID_STL=c++_static` (single self-contained `.so`, no bundled `libc++_shared.so`) and `ANDROID_PLATFORM` aligned to `minSdk` (24).
> - **L8**: content-based format detection centralized in `commonMain/ImageFormats.kt` — WEBP now requires the `RIFF` container, and BMP/GIF/HEIF are detected by magic bytes (removed two duplicated per-platform `detectFormat`s).
> - **L9**: `publish-ios.yml` reworked to a manual `workflow_dispatch` (validated `version` input) that builds from main, commits the checksum, then creates the tag ONCE — no force-move, so an in-flight SPM resolve can't hit a mismatched `Package.swift`. **Behavior change**: iOS releases are now cut by running the workflow, not by pushing a `vX.Y.Z` tag (documented in CLAUDE.md).
>
> Verified: 45 tests green on iOS simulator (adds the `ImageFormats` detection suite) and 10 instrumented tests on an API 35 emulator against a `.so` rebuilt with `c++_static`/`sysconf`/`android-24`; full `:shared:build` + `ktfmtCheck` green; C++ syntax-checked in both HAVE_LIBAVIF modes; all three workflow YAMLs parse.

### L1. Empty input crashes with the wrong exception type (iOS)
`AvifConverter.ios.kt:777-781` — `ByteArray.toNSData()` calls `pinned.addressOf(0)` which throws `ArrayIndexOutOfBoundsException` on an empty array, escaping the `AvifError` hierarchy. Guard: `if (isEmpty()) return NSData()`. Same guard in `NSData.toByteArray()`.

### L2. CPU-bound encode on `Dispatchers.IO` (Android)
AV1 encoding is CPU-bound; running it on the unbounded-ish IO pool invites oversubscription when converting in parallel. Use `Dispatchers.Default` for the encode/decode calls, keep IO for file reads/writes. (iOS already uses `Default` for everything — acceptable.)

### L3. `maxThreads = 4` hardcoded
`avif_jni_wrapper.cpp:74,247`, `AvifConverter.ios.kt:494,547`. Use runtime CPU count (`Runtime.getRuntime().availableProcessors()` passed via JNI / `NSProcessInfo.processInfo.activeProcessorCount`).

### L4. `AvifSamples.kt` ships in the published artifact
185 lines of example code (with `println`) compiled into every consumer's dependency. Move to README/docs or the demo app source sets.

### L5. Documentation drift
- CLAUDE.md dependency table: coroutines "1.8.0" (actual 1.11.0), AGP "8.11.2" (actual 9.2.1), Compose "1.9.1" (actual 1.9.3), exifinterface "1.3.7" (actual 1.4.2).
- CLAUDE.md references `shared/src/androidMain/cpp/AGENTS.md` (C++ moved to `shared-native`).
- `shared-native/build.gradle.kts` comment still says "Without libavif: JPEG fallback" (removed in v0.2.3).
- README line 275 links "Direct XCFramework" to the v0.2.3 release.
- `publish-ios.yml` release-notes template claims "iOS: libavif resolved via SPM/CocoaPods automatically" (it's embedded, not resolved).
One doc sweep commit fixes all of these.

### L6. Repo hygiene
- `shared/src/androidMain/cpp/libavif` is a leftover from the pre-`shared-native` layout (the untracked dir in `git status`) — delete it; everything now reads from `shared-native/src/main/cpp/libavif`.
- Local `main` is one commit behind `origin/main` (the 0.3.1 checksum commit) and the local `v0.3.1` tag is stale (workflow force-moves tags): `git pull --rebase && git fetch --tags --force`. (Remote state itself was verified correct: tag v0.3.1 → Package.swift → v0.3.1 asset + matching checksum.)
- Root `Shared.xcframework.zip` (5.8 MB) is a local build leftover (git-ignored, just clutter).

### L7. Native build config nits
- `ANDROID_STL=c++_shared` for one small `.so` pulls `libc++_shared.so` into the AAR; the wrapper uses only `<vector>`/`<string>` — `c++_static` is simpler for a library and avoids consumer STL-version clashes.
- `ANDROID_PLATFORM=android-21` (shared-native CMake args) vs `minSdk = 24` (version catalog) — align to 24.

### L8. Weak non-AVIF magic-byte detection
`detectFormat` identifies WEBP by bytes 8–9 (`WE`) without checking `RIFF` at 0–3; BMP/GIF/HEIF are only detected by file extension, never by content. Fine to fix opportunistically when centralizing detection for H3.

### L9. Tag force-move can break in-flight SPM resolves
`publish-ios.yml` retags `vX.Y.Z` after committing the checksum. A consumer resolving between release creation and retag pins the wrong revision/checksum. Lower-churn alternative: compute checksum from a locally-built zip, commit `Package.swift`, **then** tag once and release — no force-move. Worth doing next time the workflow is touched, not urgent.

---

## Suggested remediation order

**Phase 1 — correctness of shipped output (target: v0.3.2)**
C1 (iOS alpha) → H2 (`isAvifSupported`) → H1 (maxSize on AVIF input) → H6 (podspec, 2-line fix) → M1 (hardware bitmaps).
Small, high-impact, mostly independent fixes.

**Phase 2 — API honesty (target: v0.4.0, minor-breaking)**
C2 + C3 (implement or remove `alphaQuality`/`lossless`/`preserveMetadata` across platforms — JNI signature change) → H3 (shared brand detection in commonMain) → H4 (decode orientation) → M2, M4.

**Phase 3 — infrastructure (parallel to both)**
H8 (CI workflow + Android round-trip + regression tests for every Phase 1/2 fix) → H5 (version single-source + workflow validation) → H7 (fail-fast placeholder).

**Phase 4 — performance & polish**
M3, M5 (+ extract adaptive logic to commonMain), M6, M7, M8, then the Low items as a batch.

---

*Review method: full read of commonMain/androidMain/iosMain sources, JNI wrapper, CMake, Gradle configs, cinterop def, build scripts, both publish workflows, podspec/Package.swift; cross-checked against published Maven Central metadata and GitHub Releases/tags. Demo apps (`composeApp`, `iosApp`) were not in scope.*