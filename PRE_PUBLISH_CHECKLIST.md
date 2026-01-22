# Pre-Publishing Checklist for v0.1.1

Complete these steps before pushing to GitHub and publishing to Maven Central and SPM.

## ✅ Phase 1: Build and Prepare

### Step 1: Clean Build
```bash
./gradlew clean
./gradlew :shared:build
```

**Expected result:** Build succeeds without errors

### Step 2: Build XCFramework for iOS
```bash
./gradlew :shared:assembleXCFramework
```

**Expected result:** XCFramework created at `shared/build/XCFrameworks/release/Shared.xcframework`

### Step 3: Create XCFramework ZIP
```bash
cd shared/build/XCFrameworks/release
zip -r Shared.xcframework.zip Shared.xcframework
```

**Expected result:** `Shared.xcframework.zip` created

### Step 4: Calculate Checksum
```bash
swift package compute-checksum Shared.xcframework.zip
```

**Expected output:** A SHA256 checksum like `abc123def456...`

**⚠️ IMPORTANT:** Copy this checksum! You'll need it in the next step.

### Step 5: Update Package.swift with Checksum
```bash
cd /Users/rizky.rachmat/Documents/ngulik/web/AvifKit
```

Open `Package.swift` and replace line 47:
```swift
checksum: "CHECKSUM_PLACEHOLDER_UPDATE_AFTER_BUILD"
```

With the actual checksum:
```swift
checksum: "your_actual_checksum_from_step_4"
```

**Example:**
```swift
.binaryTarget(
    name: "Shared",
    url: "https://github.com/alfikri-rizky/AvifKit/releases/download/v0.1.1/Shared.xcframework.zip",
    checksum: "8bad9c562a2e76a7f5ce9af40fa0a7a44f8c02f81313a81c2210cab7e9e571fe"
),
```

---

## ✅ Phase 2: Verification

### Step 6: Verify All Version Numbers
Check that all files have version `0.1.1`:

- [ ] `gradle.properties` line 108: `VERSION_NAME=0.1.1` ✅
- [ ] `shared/build.gradle.kts` line 105: `version = "0.1.1"` ✅
- [ ] `AvifKit.podspec` line 3: `s.version = '0.1.1'` ✅
- [ ] `Package.swift` line 46: `v0.1.1` in URL ✅
- [ ] `Package.swift` line 47: Actual checksum (not placeholder) ⚠️
- [ ] `README.md`: All examples use `0.1.1` ✅
- [ ] `shared/README.md`: All examples use `0.1.1` ✅

### Step 7: Verify Artifact Configuration
Check `shared/build.gradle.kts` has explicit coordinates:

```kotlin
coordinates(
    groupId = "io.github.alfikri-rizky",
    artifactId = "avifkit",
    version = "0.1.1"
)
```

- [ ] Artifact ID is `avifkit` (not `shared`) ✅

### Step 8: Test Build Locally (Optional but Recommended)
```bash
# Test Android build
./gradlew :composeApp:assembleDebug

# Test iOS build (if on macOS)
xcodebuild -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  build
```

---

## ✅ Phase 3: Git Operations

### Step 9: Check Git Status
```bash
git status
```

**You should see changes in:**
- `gradle.properties`
- `shared/build.gradle.kts`
- `AvifKit.podspec`
- `Package.swift`
- `README.md`
- `shared/README.md`
- `PUBLISH_V0.1.1.md` (new)
- `PRE_PUBLISH_CHECKLIST.md` (new, this file)

### Step 10: Stage All Changes
```bash
git add -A
```

### Step 11: Commit Changes
```bash
git commit -m "Release v0.1.1 - Fix artifact naming and update documentation

Changes:
- Fix Maven artifact name from 'shared' to 'avifkit'
- Add explicit coordinates in build.gradle.kts
- Update all version references to 0.1.1
- Update documentation with correct installation instructions
- Add PlatformFile API examples
- Clarify automatic libavif integration for both platforms

Breaking changes:
- Artifact name changed from io.github.alfikri-rizky:shared to io.github.alfikri-rizky:avifkit

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Step 12: Create and Push Tag
```bash
git tag v0.1.1
git push origin main
git push origin v0.1.1
```

**Expected result:** Changes and tag pushed to GitHub

---

## ✅ Phase 4: GitHub Release

### Step 13: Create GitHub Release
```bash
gh release create v0.1.1 \
  --title "Release v0.1.1 - Fixed Artifact Naming" \
  --notes "## 🎉 What's Changed

### Fixed Artifact Name
The library is now correctly published as \`io.github.alfikri-rizky:avifkit:0.1.1\` instead of \`shared\`.

### Installation

#### Kotlin Multiplatform (Recommended)
\`\`\`kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(\"io.github.alfikri-rizky:avifkit:0.1.1\")
        }
    }
}
\`\`\`

#### Android Only
\`\`\`kotlin
dependencies {
    implementation(\"io.github.alfikri-rizky:avifkit:0.1.1\")
}
\`\`\`

#### iOS - Swift Package Manager
\`\`\`swift
.package(url: \"https://github.com/alfikri-rizky/AvifKit.git\", from: \"0.1.1\")
\`\`\`

#### iOS - CocoaPods
\`\`\`ruby
pod 'AvifKit', '~> 0.1.1'
\`\`\`

## ✨ Features

- ✅ AVIF encoding/decoding via native libavif
- ✅ Adaptive compression (SMART & STRICT strategies)
- ✅ PlatformFile API for cross-platform file operations
- ✅ Multi-threaded processing
- ✅ Automatic fallback to JPEG
- ✅ Android: Native binaries bundled in AAR
- ✅ iOS: libavif automatically resolved via SPM/CocoaPods

## 📦 What's Included

- Full AVIF encoding/decoding support
- Native libavif for both platforms
- Cross-platform file handling with PlatformFile
- Quality presets and compression strategies
- Automatic dependency resolution

## 🔗 Links

- [Maven Central](https://central.sonatype.com/artifact/io.github.alfikri-rizky/avifkit/0.1.1)
- [Documentation](https://github.com/alfikri-rizky/AvifKit/blob/main/README.md)
- [libavif Integration Guide](https://github.com/alfikri-rizky/AvifKit/blob/main/docs/LIBAVIF_INTEGRATION.md)" \
  shared/build/XCFrameworks/release/Shared.xcframework.zip
```

**Expected result:** GitHub release created with XCFramework attached

**Verify:** Go to https://github.com/alfikri-rizky/AvifKit/releases and confirm the release exists with the zip file.

---

## ✅ Phase 5: Maven Central Publishing

### Step 14: Publish to Maven Central
```bash
./gradlew :shared:publishToMavenCentral --no-configuration-cache
```

**Expected output:**
```
Publishing to Maven Central...
✓ Artifacts signed
✓ Uploaded to staging repository
```

### Step 15: Login to Maven Central Portal
1. Go to https://central.sonatype.com/
2. Login with your credentials
3. Click "Deployments" in the left sidebar
4. Find your deployment (should be at the top)

### Step 16: Verify Deployment Contents
Check that the deployment includes:
- [ ] `avifkit-0.1.1.aar` (Android)
- [ ] `avifkit-0.1.1.module` (Gradle metadata)
- [ ] `avifkit-0.1.1.pom` (Maven metadata)
- [ ] `avifkit-0.1.1-sources.jar` (Sources)
- [ ] All files signed with `.asc` signatures

**⚠️ VERIFY:** The artifact name is `avifkit` (not `shared`)!

### Step 17: Publish the Deployment
1. Click on the deployment
2. Click "Publish" button
3. Confirm publication

**Expected result:**
- Status changes to "Published"
- Artifact will be available on Maven Central in 10-15 minutes

### Step 18: Verify Maven Central Publication (After 15 minutes)
Check the artifact is live:
```bash
open "https://central.sonatype.com/artifact/io.github.alfikri-rizky/avifkit/0.1.1"
```

**Expected result:** Artifact page loads showing version 0.1.1

---

## ✅ Phase 6: SPM Verification

### Step 19: Verify SPM Package Resolution
SPM will automatically work once:
- [x] GitHub release v0.1.1 exists
- [x] Shared.xcframework.zip is attached to the release
- [x] Package.swift has correct URL and checksum

**Test it:**
1. Create a test iOS project in Xcode
2. File → Add Packages...
3. Enter: `https://github.com/alfikri-rizky/AvifKit`
4. Select version 0.1.1
5. Click "Add Package"

**Expected result:** Package resolves successfully with libavif dependency

---

## ✅ Phase 7: CocoaPods (Optional)

### Step 20: Validate Podspec
```bash
pod spec lint AvifKit.podspec --allow-warnings
```

**Expected result:** Validation passes

### Step 21: Check CocoaPods Trunk Registration
```bash
pod trunk me
```

**If not registered:**
```bash
pod trunk register rizkyalfikri@gmail.com 'Rizky Alfikri'
```

Check email and confirm registration.

### Step 22: Push to CocoaPods Trunk
```bash
pod trunk push AvifKit.podspec --allow-warnings
```

**Expected result:** Podspec accepted and published

### Step 23: Verify CocoaPods Publication
```bash
pod search AvifKit
```

**Expected output:**
```
-> AvifKit (0.1.1)
   Kotlin Multiplatform library for AVIF image conversion on iOS and Android
   pod 'AvifKit', '~> 0.1.1'
   - Homepage: https://github.com/alfikri-rizky/AvifKit
   - Source:   https://github.com/alfikri-rizky/AvifKit.git
   - Versions: 0.1.1 [trunk repo]
```

---

## 📋 Final Verification Checklist

After all publishing is complete, verify:

### Android (Maven Central)
- [ ] Visit https://central.sonatype.com/artifact/io.github.alfikri-rizky/avifkit/0.1.1
- [ ] Artifact name is `avifkit` (not `shared`)
- [ ] Version 0.1.1 is visible
- [ ] AAR file is downloadable

### iOS (SPM)
- [ ] GitHub release v0.1.1 exists
- [ ] XCFramework zip is attached
- [ ] Package.swift has correct checksum
- [ ] Can add package in Xcode successfully

### iOS (CocoaPods)
- [ ] `pod search AvifKit` shows version 0.1.1
- [ ] Podspec is live on trunk

### Documentation
- [ ] README.md has correct installation instructions
- [ ] shared/README.md has correct installation instructions
- [ ] All links work

---

## 🎉 Success Criteria

Your library is successfully published when:

1. ✅ Maven Central shows `io.github.alfikri-rizky:avifkit:0.1.1`
2. ✅ SPM can resolve the package with libavif dependency
3. ✅ CocoaPods shows version 0.1.1 (if published)
4. ✅ GitHub release exists with XCFramework

Users can now install with:
```kotlin
// Android/KMM
implementation("io.github.alfikri-rizky:avifkit:0.1.1")
```

```swift
// iOS SPM
.package(url: "https://github.com/alfikri-rizky/AvifKit.git", from: "0.1.1")
```

```ruby
# iOS CocoaPods
pod 'AvifKit', '~> 0.1.1'
```

---

## 🚨 Troubleshooting

### Issue: "Checksum mismatch" in SPM
**Solution:** Recalculate checksum and update Package.swift, commit and push

### Issue: Maven Central shows "shared" instead of "avifkit"
**Solution:** The explicit coordinates() in build.gradle.kts should fix this. If not, check the staging repository before publishing.

### Issue: GitHub release upload fails
**Solution:** Upload manually via GitHub web interface

### Issue: CocoaPods validation fails
**Solution:** Run with `--verbose` to see detailed errors

---

## 📞 Need Help?

If you encounter issues during publishing:
1. Check the logs for specific error messages
2. Refer to `PUBLISH_V0.1.1.md` for detailed troubleshooting
3. Open an issue if you're stuck

Good luck with the release! 🚀
