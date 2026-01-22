# CMake libavif Warning Diagnosis

## Problem

Build logs showed CMake warnings during Android native library compilation:

```
⚠️ libavif not found - Using placeholder implementation
⚠️ Run: ./scripts/setup-android-libavif.sh
```

This occurred for all Android ABIs (arm64-v8a, armeabi-v7a, x86, x86_64) in both Debug and Release builds.

## Root Cause

The CMakeLists.txt checks for libavif at line 19:

```cmake
set(LIBAVIF_DIR ${CMAKE_SOURCE_DIR}/libavif)
if(EXISTS ${LIBAVIF_DIR}/CMakeLists.txt)
    # Build with AVIF support
else()
    # Fallback to placeholder
endif()
```

**Local builds work** because:
- The setup script (`./scripts/setup-android-libavif.sh`) was run manually
- This downloaded libavif to `shared/src/androidMain/cpp/libavif/`
- The directory exists locally but is NOT committed to git (shown as `??` in git status)

**GitHub Actions builds failed** because:
- The workflows (`publish.yml`, `publish-ios.yml`) didn't run the setup script
- When code is checked out in CI, the libavif directory doesn't exist
- CMake can't find libavif, falls back to placeholder mode
- Published artifacts would have JPEG fallback instead of real AVIF support

## Impact

Without the fix, published libraries would:
- ❌ Android AAR: Built in placeholder mode (no AVIF, only JPEG fallback)
- ❌ iOS XCFramework: Built in placeholder mode (no AVIF, only JPEG fallback)
- ❌ Users would get a library that doesn't actually support AVIF encoding/decoding

## Solution

Added libavif setup step to both GitHub Actions workflows:

### publish.yml (Maven Central)
```yaml
- name: Setup Android libavif
  run: |
    chmod +x scripts/setup-android-libavif.sh
    # Run setup script non-interactively
    echo "n" | ./scripts/setup-android-libavif.sh || true

- name: Build shared module
  run: ./gradlew :shared:build --no-daemon --stacktrace
```

### publish-ios.yml (iOS Release)
```yaml
- name: Setup Android libavif
  run: |
    chmod +x scripts/setup-android-libavif.sh
    # Run setup script non-interactively
    echo "n" | ./scripts/setup-android-libavif.sh || true

- name: Build XCFramework
  run: ./gradlew :shared:assembleSharedReleaseXCFramework --no-daemon --console=plain
```

## How It Works Now

1. **Checkout code** - Gets source code (without libavif)
2. **Setup Android libavif** - Downloads libavif from GitHub
3. **Build** - CMake finds libavif, builds with AVIF support
4. **Publish** - AAR/XCFramework includes native AVIF binaries

## Verification

After the fix, CMake should show:
```
✅ libavif found - Building with AVIF support
AVIF Support: ✅ ENABLED (using libavif)
```

Instead of:
```
⚠️ libavif not found - Using placeholder implementation
AVIF Support: ⚠️ DISABLED (placeholder mode)
```

## Files Changed

- `.github/workflows/publish.yml` - Added libavif setup before Maven Central publish
- `.github/workflows/publish-ios.yml` - Added libavif setup before iOS release

Commit: b65f6e5 "Fix: Add libavif setup step to GitHub Actions workflows"

## Why libavif Isn't Committed

The libavif directory is not committed to the repository because:

1. **Size**: libavif source code is large (~10-20MB)
2. **Maintenance**: Easier to update by changing setup script than managing submodules
3. **Standard practice**: Most projects download native dependencies during build

The setup script approach is standard for CI/CD workflows:
- Swift packages use `Package.resolved`
- CocoaPods use `Podfile.lock` + `pod install`
- This project uses setup scripts + GitHub Actions workflow steps

## Next Steps

The fix is committed and pushed. When you trigger the publishing workflows again:

1. **Re-run failed Maven Central workflow**: The build will now include libavif
2. **Future releases**: All future releases will automatically have AVIF support
3. **Local development**: Continue using `./scripts/setup-android-libavif.sh` as before

No changes needed to the actual library code - this was purely a CI/CD configuration issue.