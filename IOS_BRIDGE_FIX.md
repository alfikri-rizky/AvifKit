# iOS Bridge Registration Fix - v0.2.7

## Critical Production Bug Fixed

**Issue**: iOS AVIF encoding/decoding was **completely broken** in production due to handler never registering.

**Affected Version**: v0.2.6 (and potentially earlier)

**Severity**: 🔴 **CRITICAL** - iOS functionality was 100% non-operational

---

## Root Cause Analysis

### The Bug

**File**: `shared/src/iosMain/swift/AVIFNativeConverter.swift` line 207

```swift
// ❌ WRONG CODE (v0.2.6)
AvifKitIos.shared.registerHandler(handler: handler)
```

### Why It Failed

Kotlin/Native exports `object` singletons to Objective-C/Swift with a specific naming convention:

```kotlin
// Kotlin code
object AvifKitIos {
    fun registerHandler(handler: IosAvifNativeHandler) { ... }
}
```

Becomes in Swift/ObjC:
```swift
// ❌ NOT: AvifKitIos.shared
// ✅ YES: AvifKitIos.companion
```

### The Impact

1. **`AvifKitAutoRegister.m`** called `AvifKitSetup.registerNativeHandler()` at library load time
2. **Swift code** attempted to call `AvifKitIos.shared.registerHandler()`
3. **Crash/silent failure** - `.shared` property doesn't exist on the ObjC exported class
4. **Handler never registered** - all subsequent AVIF operations failed
5. **User sees**: `AvifError.EncodingFailed: Native AVIF handler not available`

---

## The Fix

**File**: `shared/src/iosMain/swift/AVIFNativeConverter.swift` line 207-208

```swift
@objc public class AvifKitSetup: NSObject {
    @objc public static func registerNativeHandler() {
        let handler = AvifKitNativeHandler()
        // ✅ FIXED: Kotlin object is exposed as .companion, not .shared
        AvifKitIos.companion.registerHandler(handler: handler)
    }
}
```

### What Changed

- ❌ `AvifKitIos.shared.registerHandler(handler: handler)`
- ✅ `AvifKitIos.companion.registerHandler(handler: handler)`

---

## How To Verify The Fix

### 1. Check Auto-Registration Logs (iOS Console)

```swift
// Success output:
[AvifKit] ✅ Native handler auto-registered successfully

// Failure output (old bug):
[AvifKit] ⚠️ AvifKitSetup found but registerNativeHandler selector not found
```

### 2. Test AVIF Conversion

```swift
import AvifKit
import UIKit

// Test encoding
let converter = AvifConverter()
let image = UIImage(named: "test.jpg")!
let input = ImageInput.from(image)

do {
    let avifData = try await converter.encodeAvif(input: input)
    print("✅ Encoded \(avifData.count) bytes")
} catch {
    print("❌ Failed: \(error)")
}
```

### 3. Check Handler Availability

```swift
import Shared

let isAvailable = AvifKitIos.companion.isNativeAvifAvailable()
print("Handler available: \(isAvailable)")  // Should be true
```

---

## Testing Checklist

- [ ] iOS demo app builds successfully
- [ ] Auto-registration logs show success message
- [ ] `converter.isAvifSupported()` returns `true`
- [ ] AVIF encoding works (JPEG → AVIF)
- [ ] AVIF decoding works (AVIF → UIImage)
- [ ] SPM integration works in fresh Xcode project
- [ ] No crashes during image conversion

---

## Related Files

| File | Purpose |
|------|---------|
| `shared/src/iosMain/swift/AVIFNativeConverter.swift:207` | **🔧 FIX LOCATION** |
| `shared/src/iosMain/kotlin/com/alfikri/rizky/avifkit/IosAvifNativeHandler.kt` | Kotlin bridge interface |
| `shared/src/iosMain/objc/AvifKitAutoRegister.m` | ObjC constructor auto-registration |
| `CLAUDE.md` | Updated with this known issue |

---

## Prevention

To prevent similar issues:

1. **Always use `AvifKitIos.companion`** when calling from Swift
2. **Never assume `.shared`** - that's a Swift convention, not Kotlin/Native
3. **Check console logs** during development for registration messages
4. **Test on real device** - simulators may behave differently

---

## Version History

- **v0.2.6**: Bug introduced (handler never registers)
- **v0.2.7**: Bug fixed (`.shared` → `.companion`)
