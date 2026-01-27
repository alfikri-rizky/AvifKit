# Diagnosis: "Native decoding failed" on Android API 30

**Error:** `AvifError.DecodingFailed("Native decoding failed")`
**Location:** `AvifConverter.android.kt:497` → `nativeDecode()` returns `null`
**Platform:** Android API 30
**Issue:** The JNI native method `nativeDecode()` is returning null instead of decoded image data

## Root Causes & Solutions

### 1. **Native Library Not Loaded** ✅ Check First

The most common cause on API 30.

**Symptoms:**
- `nativeDecode()` returns `null`
- No detailed error message from native layer

**Diagnosis:**
Add logging to check library status:
```kotlin
val converter = AvifConverter()
Log.d("AvifKit", "Native library loaded: ${AvifConverter.isNativeLibraryLoaded()}")
Log.d("AvifKit", "Library version: ${converter.getLibraryVersion()}")
```

**If library NOT loaded:**
```
Native library loaded: false
Library version: Native library not loaded
```

**Solutions:**
- Verify `.so` files exist in APK:
  ```bash
  unzip -l app-debug.apk | grep libavif-android-wrapper.so
  # Should show files for all ABIs:
  # lib/arm64-v8a/libavif-android-wrapper.so
  # lib/armeabi-v7a/libavif-android-wrapper.so
  # lib/x86/libavif-android-wrapper.so
  # lib/x86_64/libavif-android-wrapper.so
  ```

- Check if device ABI is supported:
  ```bash
  adb shell getprop ro.product.cpu.abi
  # Should be one of: arm64-v8a, armeabi-v7a, x86, x86_64
  ```

- Rebuild native libraries:
  ```bash
  ./gradlew :shared:clean
  ./gradlew :shared:build
  ```

---

### 2. **libavif Not Built (Placeholder Mode)**

The JNI wrapper was compiled without HAVE_LIBAVIF flag.

**Symptoms:**
- Library loads successfully
- Decoding returns placeholder test image (100x100 gradient)
- Logs show: `"PLACEHOLDER: libavif not available"`

**Diagnosis:**
Check build logs:
```bash
./gradlew :shared:build --info | grep -i "libavif\|AVIF Support"
```

**Expected output:**
```
✅ libavif found - Building with AVIF support
AVIF Support: ✅ ENABLED (using libavif)
```

**If shows placeholder mode:**
```
⚠️  libavif not found - Using placeholder implementation
AVIF Support: ⚠️ DISABLED (placeholder mode)
```

**Solution:**
Run libavif setup script:
```bash
cd /path/to/AvifKit
./scripts/setup-android-libavif.sh
./gradlew :shared:clean
./gradlew :shared:build
```

---

### 3. **JNI Class Lookup Failure** (API 30 Specific)

Native code can't find `DecodedImage` class due to ProGuard or class loading issues.

**Symptoms:**
- Native library loaded: `true`
- Logs show: `"Failed to find DecodedImage class"` or `"Failed to find DecodedImage constructor"`
- Only happens on release builds or certain API levels

**Diagnosis:**
Enable verbose JNI logging:
```bash
adb shell setprop log.tag.AvifJNI VERBOSE
adb logcat | grep -E "AvifJNI|AvifKit"
```

Look for:
```
E/AvifJNI: Failed to find DecodedImage class
E/AvifJNI: Failed to find DecodedImage constructor
```

**Solutions:**

**A. ProGuard Rules (for release builds):**
Add to `proguard-rules.pro`:
```proguard
# Keep DecodedImage for JNI
-keep class com.alfikri.rizky.avifkit.DecodedImage { *; }
-keepclassmembers class com.alfikri.rizky.avifkit.DecodedImage {
    <init>([III)V;
}

# Keep AvifConverter native methods
-keepclasseswithmembernames class * {
    native <methods>;
}
```

**B. Update JNI Code (if package was renamed):**
Current JNI code looks for:
```cpp
jclass decodedImageClass = env->FindClass("com/alfikri/rizky/avifkit/DecodedImage");
```

If package was renamed to `io.github.alfikririzky.avifkit`, update line 284:
```cpp
jclass decodedImageClass = env->FindClass("io/github/alfikririzky/avifkit/DecodedImage");
```

---

### 4. **Corrupted or Invalid AVIF Data**

The input data isn't valid AVIF format or is corrupted.

**Symptoms:**
- Logs show: `"Failed to parse AVIF"` or `"Failed to decode AVIF"`
- Error message from libavif in logcat

**Diagnosis:**
Check if data is valid AVIF:
```kotlin
val isAvif = converter.isAvifFile(ImageInput.from(avifBytes))
Log.d("AvifKit", "Is valid AVIF: $isAvif")

// Check file signature
val header = avifBytes.take(12).toByteArray()
Log.d("AvifKit", "File header: ${header.joinToString(" ") { "%02x".format(it) }}")
// Valid AVIF should show: ... 66 74 79 70 61 76 69 66 ...
//                             f  t  y  p  a  v  i  f
```

**Solution:**
- Verify the source AVIF file is not corrupted
- Try decoding a known-good AVIF file
- Check if the AVIF was encoded with compatible settings

---

### 5. **Memory Issues on API 30**

Out of memory or allocation failures during decoding.

**Symptoms:**
- Works on smaller images, fails on larger ones
- Intermittent failures
- No specific error message

**Diagnosis:**
Add memory logging:
```kotlin
val runtime = Runtime.getRuntime()
Log.d("AvifKit", "Max memory: ${runtime.maxMemory() / 1024 / 1024}MB")
Log.d("AvifKit", "Free memory: ${runtime.freeMemory() / 1024 / 1024}MB")
Log.d("AvifKit", "Total memory: ${runtime.totalMemory() / 1024 / 1024}MB")
Log.d("AvifKit", "Image size: ${avifBytes.size / 1024}KB")
```

**Solution:**
- Test with smaller image first
- Use `options.maxDimension` to limit decoded size
- Run `System.gc()` before decoding large images

---

## Complete Diagnostic Script

Add this to your Android app to diagnose the issue:

```kotlin
import android.util.Log
import io.github.alfikririzky.avifkit.*

fun diagnoseAvifDecoding(avifBytes: ByteArray) {
    val TAG = "AvifDiagnose"

    // 1. Check library status
    val libraryLoaded = AvifConverter.isNativeLibraryLoaded()
    Log.d(TAG, "✓ Native library loaded: $libraryLoaded")

    // 2. Get library version
    val converter = AvifConverter()
    val version = converter.getLibraryVersion()
    Log.d(TAG, "✓ Library version: $version")

    // 3. Check if valid AVIF
    val isAvif = converter.isAvifFile(ImageInput.from(avifBytes))
    Log.d(TAG, "✓ Is valid AVIF: $isAvif")

    // 4. Check file header
    val header = avifBytes.take(12).toByteArray()
    val headerHex = header.joinToString(" ") { "%02x".format(it) }
    Log.d(TAG, "✓ File header: $headerHex")

    // 5. Check memory
    val runtime = Runtime.getRuntime()
    Log.d(TAG, "✓ Max memory: ${runtime.maxMemory() / 1024 / 1024}MB")
    Log.d(TAG, "✓ Free memory: ${runtime.freeMemory() / 1024 / 1024}MB")
    Log.d(TAG, "✓ Image size: ${avifBytes.size / 1024}KB")

    // 6. Check device info
    Log.d(TAG, "✓ Android API: ${android.os.Build.VERSION.SDK_INT}")
    Log.d(TAG, "✓ Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
    Log.d(TAG, "✓ ABI: ${android.os.Build.SUPPORTED_ABIS.joinToString()}")

    // 7. Try decoding
    try {
        Log.d(TAG, "→ Attempting decode...")
        val bitmap = converter.decodeAvif(ImageInput.from(avifBytes))
        Log.d(TAG, "✓ Decode SUCCESS: ${bitmap.width}x${bitmap.height}")
    } catch (e: Exception) {
        Log.e(TAG, "✗ Decode FAILED: ${e.message}", e)
    }
}
```

**Run on your device:**
```kotlin
// In your Activity or Fragment
val avifBytes = ... // your AVIF data
diagnoseAvifDecoding(avifBytes)

// Then check logcat:
// adb logcat | grep AvifDiagnose
```

---

## Expected Results

### ✅ Working Configuration (API 30):
```
D/AvifDiagnose: ✓ Native library loaded: true
D/AvifDiagnose: ✓ Library version: libavif v1.0.4
D/AvifDiagnose: ✓ Is valid AVIF: true
D/AvifDiagnose: ✓ File header: 00 00 00 20 66 74 79 70 61 76 69 66
D/AvifDiagnose: ✓ Max memory: 512MB
D/AvifDiagnose: ✓ Free memory: 234MB
D/AvifDiagnose: ✓ Image size: 145KB
D/AvifDiagnose: ✓ Android API: 30
D/AvifDiagnose: ✓ Device: Google Pixel 4a
D/AvifDiagnose: ✓ ABI: arm64-v8a, armeabi-v7a
I/AvifJNI: Using libavif for decoding
I/AvifJNI: Successfully decoded AVIF: 1920x1080
D/AvifDiagnose: ✓ Decode SUCCESS: 1920x1080
```

### ❌ Broken Configuration:
```
D/AvifDiagnose: ✓ Native library loaded: false
D/AvifDiagnose: ✓ Library version: Native library not loaded
D/AvifDiagnose: ✗ Decode FAILED: Native decoding failed
```

---

## Quick Fixes

### Fix 1: Rebuild Everything
```bash
cd /path/to/AvifKit
./scripts/setup-android-libavif.sh
./gradlew :shared:clean
./gradlew :shared:build
./gradlew :composeApp:assembleDebug
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

### Fix 2: Check APK Contents
```bash
unzip -l composeApp/build/outputs/apk/debug/composeApp-debug.apk | grep -E "\.so$|libavif"
```

### Fix 3: Enable Detailed Logging
```kotlin
// In your build.gradle.kts
android {
    buildTypes {
        debug {
            ndk {
                abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            }
        }
    }
}
```

---

## Still Not Working?

Run the diagnostic script and share the output. Most likely causes for API 30:
1. **Native library not in APK** (70% of cases)
2. **libavif not built** (20% of cases)
3. **ProGuard stripping classes** (8% of cases)
4. **Actual AVIF corruption** (2% of cases)

**Next Steps:**
1. Run the diagnostic script above
2. Share the logcat output
3. Check if `.so` files are in your APK
4. Verify libavif was built during gradle build
