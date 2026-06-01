# iOS Architecture Fix — Self-Contained Kotlin/Native klib via cinterop

**Status:** ✅ Implemented in v0.3.0 (2026-06-01)
**Author analysis date:** 2026-05-31
**Affects:** All Compose Multiplatform consumers that add `io.github.alfikri-rizky:avifkit` in `commonMain`
**Supersedes:** The Swift-handler registration model (`AvifKitIos` / `AvifKitSetup.registerNativeHandler()` / `AvifKitAutoRegister.m`), now **removed**.

> **Implementation note (v0.3.0):** `AvifConverter.ios.kt` now calls `libavif`
> directly via the `libavif` cinterop (`shared/src/nativeInterop/cinterop/libavif.def`).
> The codec static libs are built by `scripts/build-ios-libavif.sh` into
> `shared/src/nativeInterop/libs/ios/<target>/` and linked via `linkerOpts` in
> `shared/build.gradle.kts`. The Swift/ObjC handler files and the SPM `AvifKit`/
> `AvifKitObjC` targets have been deleted; the SPM `AvifKit` product now vends the
> self-contained `Shared` XCFramework directly.

---

## 1. Symptom

A Compose Multiplatform consumer adds the dependency in `commonMain`:

```kotlin
commonMain.dependencies {
    implementation("io.github.alfikri-rizky:avifkit:0.2.10")
}
```

and the iOS app additionally links the SPM `AvifKit` product. At runtime, every conversion fails with:

```
Conversion failed: Native AVIF handler not available.
Ensure the AvifKit Swift target is linked in your project.
If using SPM, add the 'AvifKit' product (not just 'Shared') as a dependency.
```

This happens **even after** calling `AvifKitSetup.registerNativeHandler()` in the app's `init()`, and **even after** the v0.2.10 "make `Shared.xcframework` dynamic" change.

---

## 2. Root cause: two Kotlin runtimes, two `AvifKitIos` singletons

AvifKit's iOS encoder is reached through a **runtime-registered handler** stored in the Kotlin `object AvifKitIos`. For it to work, the singleton the **Swift bridge registers into** must be the **same instance** the consumer's `AvifConverter` **reads from**.

In a Compose Multiplatform consumer there are **two separate compilations** of AvifKit's Kotlin code, producing **two distinct `AvifKitIos` singletons**:

| Instance | Lives in | Built from | Touched by |
|----------|----------|-----------|------------|
| **A** | `ComposeApp.framework` (embedded by Gradle `embedAndSignAppleFrameworkForXcode`) | The **Maven iOS klib** `avifkit-iosarm64` / `avifkit-iossimulatorarm64`, compiled into the consumer's own framework | The ViewModel's `AvifConverter().convertToFile()` — **reads** here |
| **B** | `Shared.xcframework` (via the SPM `AvifKit` product) | The published XCFramework | The Swift `AvifKitSetup.registerNativeHandler()` — **writes** here |

The Swift bridge registers the handler into **B**; the conversion looks it up in **A**, finds `null`, and throws.

```
                consumer commonMain code
                          │
                          ▼
   ┌──────────────────────────────────────┐
   │ ComposeApp.framework  (Gradle klib)   │
   │   AvifConverter ── reads ──► AvifKitIos (A)  ← always empty
   └──────────────────────────────────────┘

   ┌──────────────────────────────────────┐
   │ Shared.xcframework    (SPM product)   │
   │   AvifKitSetup ── writes ──► AvifKitIos (B)  ← handler lands here
   │   AvifKitNativeHandler ─► avif.swift          │
   └──────────────────────────────────────┘
```

`dyld` cannot merge A and B: they are two different frameworks built from the same source. **Dynamic-vs-static is irrelevant** here, because instance A is not inside `Shared.xcframework` at all — it is a separate static compilation linked into `ComposeApp.framework`.

### 2.1 Why no in-place registration/discovery fix can work

`AvifConverter.ios.kt` has a lazy `AvifKitIos.getOrDiscoverHandler()` that uses ObjC reflection (`NSClassFromString("AvifKitSetup")` → `performSelector("registerNativeHandler")`). This **cannot** bridge A and B, because the Swift method body is compile-time bound to `Shared`:

```swift
@objc public class AvifKitSetup: NSObject {
    @objc public static func registerNativeHandler() {
        let handler = AvifKitNativeHandler()
        AvifKitIos.shared.registerHandler(handler: handler)   // ← bound to Shared.xcframework (B)
    }
}
```

No matter **who** invokes `registerNativeHandler()` (the app, or klib-A via reflection), the handler is written into **B**. klib-A's own `AvifKitIos` (A) stays empty. The registration approach is therefore unsalvageable for a Gradle-distributed iOS klib.

> **Note on `.shared` vs `.companion`:** `AvifKitIos` is a top-level `object`, so Kotlin/Native exports its accessor as `.shared` (verified in the generated `Shared.h`: `@property (class, readonly, getter=shared) ... swift_name("shared")`). The current Swift code `AvifKitIos.shared` is **correct**. `IOS_BRIDGE_FIX.md`, which recommends `.companion`, is **wrong** — `.companion` does not compile for a top-level object. That document should be corrected or removed.

---

## 3. The asymmetry to eliminate

Android and iOS reach native code in fundamentally different ways. **That difference is the bug.**

| | Android | iOS (current) |
|---|---------|----------------|
| How Kotlin reaches the codec | **Direct JNI** from the klib: `external fun nativeEncode(...)`, `System.loadLibrary("avif-android-wrapper")` | Calls **out** to a Swift handler supplied from **outside** the klib (SPM `AvifKit` target) |
| Native artifact distribution | Ships transitively with the Maven artifact (`avifkit-native` `.so`) | Must be linked separately via SPM → second Kotlin runtime |
| Number of Kotlin runtimes in a KMP consumer | One | **Two** |
| Consumer setup | Add one Gradle line | Gradle line **+** SPM package **+** `registerNativeHandler()` — and still broken |

**Goal: make iOS behave like Android.** The klib should call the native AVIF codec **directly**, with no external Swift, no handler registration, and no second runtime.

---

## 4. Solution: call `libavif` directly from Kotlin/Native via cinterop

avif.swift sits on top of a real C library (`libavif` / `avifc`, with `libaom` + `libdav1d` + `libSvtAv1Enc` xcframeworks underneath). Kotlin/Native binds to C directly via **cinterop** — the canonical KMP mechanism, and the exact analog of the Android JNI path.

### 4.1 Target architecture

```
                consumer commonMain code
                          │
                          ▼
   ┌──────────────────────────────────────────────┐
   │ ComposeApp.framework   (the ONLY framework)    │
   │   AvifConverter.ios ── cinterop ──► libavif C  │
   │                                     (libaom,   │
   │                                      libdav1d) │
   └──────────────────────────────────────────────┘
```

- **One framework, one runtime, one code path.**
- **No SPM package, no `import AvifKit` in Swift, no `registerNativeHandler()`** for KMP consumers.
- Symmetric with Android (klib calls native directly; codec ships with the artifact).

### 4.2 What gets deleted

- `shared/src/iosMain/kotlin/com/alfikri/rizky/avifkit/IosAvifNativeHandler.kt` (the `AvifKitIos` registry + `getOrDiscoverHandler`)
- `shared/src/iosMain/swift/AVIFNativeConverter.swift` and `AvifKitExports.swift`
- `shared/src/iosMain/objc/AvifKitAutoRegister.m`
- The SPM `AvifKit` + `AvifKitObjC` Swift targets in `Package.swift`
- The `IOS_BRIDGE_FIX.md` workaround doc

> **Optional:** keep the Swift API as a *separate, pure-Swift convenience product* for Swift-only (non-KMP) apps — but it must be fully decoupled from the KMP consumer path. Do not let a KMP consumer depend on both.

### 4.3 What gets added

1. **A cinterop `.def`** binding `libavif`'s C API.
2. **Codec binaries** (`libavif`, `libaom`, `libdav1d`, `libSvtAv1Enc`) vendored as xcframeworks that link into the consumer app — the iOS analog of the Android `avifkit-native` `.so`.
3. **A rewrite** of `AvifConverter.ios.kt`'s encode/decode to call the cinterop C API.

---

## 5. Implementation plan

### Step 1 — Vendor the codec xcframeworks (gating decision)

The codecs currently arrive through avif.swift's SPM graph. For a self-contained klib they must ship with the Maven artifact. Options:

- **5a (recommended):** Vendor prebuilt `libavif.xcframework` + `libaom` + `libdav1d` (+ `libSvtAv1Enc` if encoding with SVT) into the AvifKit repo / release assets, and link them from the klib via cinterop `linkerOpts`. Mirrors how `avifkit-native` ships the Android `.so`.
- **5b:** Build `libavif` from source in CI for the three iOS targets and publish the resulting xcframeworks as release assets.

> This packaging choice is the main gating factor. The rest of the plan assumes the codec static libs/xcframeworks are available at a known path at klib build time.

### Step 2 — cinterop `.def`

Create `shared/src/nativeInterop/cinterop/libavif.def`:

```
headers = avif/avif.h
headerFilter = avif/**
package = libavif

# Static codec libraries that provide the libavif + aom + dav1d symbols.
# Adjust paths to wherever Step 1 places the vendored binaries.
staticLibraries = libavif.a libaom.a libdav1d.a
libraryPaths = shared/src/nativeInterop/libs/ios

linkerOpts = -framework Accelerate
```

### Step 3 — Wire cinterop into `shared/build.gradle.kts`

```kotlin
listOf(iosArm64(), iosX64(), iosSimulatorArm64()).forEach { iosTarget ->
    iosTarget.compilations.getByName("main").cinterops {
        val libavif by creating {
            defFile(project.file("src/nativeInterop/cinterop/libavif.def"))
            // packageName, includeDirs, etc. as needed
        }
    }
    iosTarget.binaries.framework {
        baseName = "Shared"   // still produced for Swift-only consumers, if kept
        isStatic = false
    }
}
```

> Per-target `libraryPaths` (device vs. simulator vs. x64) will differ; resolve them per `iosTarget.name`.

### Step 4 — Rewrite `AvifConverter.ios.kt`

Replace the handler lookup:

```kotlin
// BEFORE
val handler = AvifKitIos.getOrDiscoverHandler()
    ?: throw AvifError.EncodingFailed("Native AVIF handler not available. ...")
val avifData = handler.encodeImageWithOptions(image, encodingOptions) ?: ...
```

with direct cinterop calls into `libavif`:

```kotlin
// AFTER (sketch — real code manages avifEncoder/avifImage lifecycles + memScoped)
import libavif.*

private fun encodeImageToAvif(rgba: ByteArray, width: Int, height: Int, options: EncodingOptions): NSData = memScoped {
    val image = avifImageCreate(width.toUInt(), height.toUInt(), 8u, AVIF_PIXEL_FORMAT_YUV420)
    // populate RGB planes from `rgba`, run avifImageRGBToYUV, configure avifEncoder
    val encoder = avifEncoderCreate()
    encoder!!.pointed.quality = options.quality
    encoder.pointed.speed = options.speed
    val output = alloc<avifRWData>()
    val res = avifEncoderWrite(encoder, image, output.ptr)
    if (res != AVIF_RESULT_OK) throw AvifError.EncodingFailed("libavif: ${avifResultToString(res)?.toKString()}")
    val data = output.data!!.readBytes(output.size.toInt()).toNSData()
    avifRWDataFree(output.ptr); avifEncoderDestroy(encoder); avifImageDestroy(image)
    data
}
```

Pixel access (UIImage → RGBA bytes) stays in Kotlin/Native via `CoreGraphics` cinterop (already available in the platform libs), so **no Swift is required**.

### Step 5 — Delete the handler/Swift/ObjC machinery

Remove the files listed in §4.2 and the SPM targets. Update `Package.swift` to either drop the `AvifKit`/`AvifKitObjC` products entirely, or keep them only as an explicitly Swift-only convenience layer.

### Step 6 — Update consumer & docs

`AvifKitCmmTest` after this change needs **only**:

```kotlin
commonMain.dependencies {
    implementation("io.github.alfikri-rizky:avifkit:<next>")
}
```

Remove from the consumer:
- the SPM `AvifKit` package reference,
- `import AvifKit` in `iOSApp.swift` / `ContentView.swift`,
- the `AvifKitSetup.registerNativeHandler()` call.

---

## 6. Migration / release notes

- **Breaking for Swift-only consumers** who relied on the SPM `AvifKit` product, unless §4.2's optional Swift product is retained.
- **Non-breaking and strictly better for KMP consumers**: they drop SPM + registration and "just add the Gradle line," matching Android.
- Bump a **minor/major** version; document the removal of `AvifKitIos` / `AvifKitSetup` from the public API.

---

## 7. Verification checklist

- [ ] cinterop binding compiles for `iosArm64`, `iosX64`, `iosSimulatorArm64`.
- [ ] Encode JPEG/PNG → AVIF from Kotlin/Native with **no Swift** and **no SPM package**.
- [ ] Decode AVIF → UIImage path works.
- [ ] `AvifKitCmmTest` converts successfully with only the `commonMain` Gradle dependency.
- [ ] No reference to `AvifKitIos` / `registerNativeHandler` remains on the consumer path.
- [ ] Codec xcframeworks link cleanly into a release (device) build and an App Store archive.
- [ ] Binary size delta documented.

---

## 8. Why not the alternatives

| Alternative | Verdict |
|-------------|---------|
| Call `registerNativeHandler()` in app `init()` | ❌ Writes to instance **B**; consumer reads **A**. Can never work for a Gradle klib consumer. |
| Make `Shared.xcframework` dynamic (v0.2.10) | ❌ Only merges duplicate `Shared` edges within SPM. Instance **A** is in `ComposeApp.framework`, not `Shared` — unaffected. |
| ObjC-reflection discovery from klib-A | ❌ The Swift method is compile-time bound to `Shared` (B); reflection still writes to B. |
| `.shared` → `.companion` | ❌ `.shared` is already correct for a top-level `object`; `.companion` does not compile. |
| **cinterop self-contained klib** | ✅ One framework, one runtime, symmetric with Android, best consumer DX. **Recommended.** |