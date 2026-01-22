# Publishing AvifKit v0.1.1 - Complete Workflow

This version fixes the artifact naming issue. The library will now be published as `io.github.alfikri-rizky:avifkit:0.1.1` (not `shared`).

## Changes Made

### Fixed Artifact Name
- ✅ **Old (incorrect):** `io.github.alfikri-rizky:shared:0.1.0`
- ✅ **New (correct):** `io.github.alfikri-rizky:avifkit:0.1.1`

### Updated Files
- ✅ `gradle.properties` - VERSION_NAME=0.1.1
- ✅ `shared/build.gradle.kts` - Added explicit coordinates(artifactId = "avifkit")
- ✅ `AvifKit.podspec` - version = 0.1.1
- ✅ `Package.swift` - URL points to v0.1.1
- ✅ `README.md` - Updated installation instructions

---

## Publishing Workflow

### Step 1: Build and Test

```bash
# Clean build
./gradlew clean

# Build shared module
./gradlew :shared:build

# Build Android demo app (optional - for testing)
./gradlew :composeApp:assembleDebug

# Build iOS XCFramework
./gradlew :shared:assembleXCFramework
```

### Step 2: Build XCFramework for iOS

```bash
# Build XCFramework
./gradlew :shared:assembleXCFramework

# Navigate to build output
cd shared/build/XCFrameworks/release

# Create zip
zip -r Shared.xcframework.zip Shared.xcframework

# Calculate checksum
swift package compute-checksum Shared.xcframework.zip

# IMPORTANT: Copy the checksum output!
# You'll need it for Package.swift
```

### Step 3: Update Package.swift with Checksum

Update `Package.swift` line 47 with the actual checksum from Step 2:

```swift
.binaryTarget(
    name: "Shared",
    url: "https://github.com/alfikri-rizky/AvifKit/releases/download/v0.1.1/Shared.xcframework.zip",
    checksum: "PASTE_ACTUAL_CHECKSUM_HERE"
),
```

### Step 4: Commit and Tag

```bash
# Go back to project root
cd /Users/rizky.rachmat/Documents/ngulik/web/AvifKit

# Stage all changes
git add -A

# Commit
git commit -m "Release v0.1.1 - Fix artifact naming

- Change artifact from 'shared' to 'avifkit'
- Add explicit Maven coordinates in build.gradle.kts
- Update all version references to 0.1.1
- iOS: libavif automatically resolved via SPM/CocoaPods
- Android: libavif bundled in AAR

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>"

# Create and push tag
git tag v0.1.1
git push origin main
git push origin v0.1.1
```

### Step 5: Create GitHub Release

```bash
# Create GitHub release with XCFramework
gh release create v0.1.1 \
  --title "Release v0.1.1 - Fixed Artifact Naming" \
  --notes "## What's Changed

- ✅ Fixed artifact naming: Now published as \`io.github.alfikri-rizky:avifkit:0.1.1\`
- ✅ Android: libavif bundled in AAR automatically
- ✅ iOS: libavif automatically resolved via SPM/CocoaPods

## Installation

### Android (Gradle)
\`\`\`kotlin
dependencies {
    implementation(\"io.github.alfikri-rizky:avifkit:0.1.1\")
}
\`\`\`

### iOS (Swift Package Manager)
\`\`\`swift
.package(url: \"https://github.com/alfikri-rizky/AvifKit.git\", from: \"0.1.1\")
\`\`\`

### iOS (CocoaPods)
\`\`\`ruby
pod 'AvifKit', '~> 0.1.1'
\`\`\`

## What's Included

- Full AVIF encoding/decoding via native libavif
- Adaptive compression with SMART/STRICT strategies
- Multi-threaded processing
- Automatic fallback to JPEG
- Native binaries for all platforms" \
  shared/build/XCFrameworks/release/Shared.xcframework.zip
```

### Step 6: Publish to Maven Central

```bash
# Publish to Maven Central
./gradlew :shared:publishToMavenCentral --no-configuration-cache

# Or use the full task name
./gradlew :shared:publishAllPublicationsToMavenCentralRepository
```

**Important:**
- Login to https://central.sonatype.com/
- Go to "Deployments"
- Find your deployment
- Click "Publish" to release it

### Step 7: Publish to CocoaPods (Optional)

```bash
# Validate podspec
pod spec lint AvifKit.podspec --allow-warnings

# If validation passes, push to trunk
pod trunk push AvifKit.podspec --allow-warnings
```

---

## Verification

### Verify Maven Central (After Publishing)

Check if the artifact is available:
```bash
# Search Maven Central (may take 10-15 minutes after publishing)
open "https://central.sonatype.com/artifact/io.github.alfikri-rizky/avifkit/0.1.1"
```

### Verify Swift Package Manager

Create a test iOS project and add the package:
```swift
dependencies: [
    .package(url: "https://github.com/alfikri-rizky/AvifKit.git", from: "0.1.1")
]
```

### Verify CocoaPods

```bash
pod search AvifKit
# Should show version 0.1.1
```

---

## Distribution Summary

Once published, users can install AvifKit with:

### Android (Maven Central)
```kotlin
implementation("io.github.alfikri-rizky:avifkit:0.1.1")
```
✅ libavif automatically included in AAR

### iOS (SPM)
```swift
.package(url: "https://github.com/alfikri-rizky/AvifKit.git", from: "0.1.1")
```
✅ libavif automatically resolved as dependency

### iOS (CocoaPods)
```ruby
pod 'AvifKit', '~> 0.1.1'
```
✅ libavif automatically installed as dependency

---

## Troubleshooting

### Maven Central says "Artifact not found"
- Wait 10-15 minutes after publishing for indexing
- Verify you clicked "Publish" in the Central Portal UI
- Check deployment status at https://central.sonatype.com/

### XCFramework checksum mismatch
- Recalculate: `swift package compute-checksum Shared.xcframework.zip`
- Update Package.swift with exact checksum
- Commit and push updated Package.swift

### CocoaPods validation fails
- Run with `--verbose` flag to see detailed errors
- Ensure GitHub release exists with correct tag
- Verify `AvifKit.podspec` source URL and version match

---

## What's Different from v0.1.0?

| Aspect | v0.1.0 (Old) | v0.1.1 (New) |
|--------|-------------|-------------|
| **Artifact Name** | `shared` ❌ | `avifkit` ✅ |
| **Maven Coordinates** | `io.github.alfikri-rizky:shared:0.1.0` | `io.github.alfikri-rizky:avifkit:0.1.1` |
| **Build Config** | Relied on gradle.properties only | Explicit `coordinates()` in build.gradle.kts |
| **Functionality** | Same | Same |

The artifact naming is now correct and consistent across all platforms!
