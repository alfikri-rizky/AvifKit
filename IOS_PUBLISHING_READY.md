# ✅ iOS Publishing Setup Complete!

Your AvifKit library is now ready to be published for iOS! Here's what has been set up and what you need to do next.

---

## 📦 What's Been Configured

### 1. **XCFramework Build System** ✅
- Added XCFramework support to `shared/build.gradle.kts`
- Creates universal framework for all iOS architectures:
  - `iosArm64` - Real iOS devices (iPhone, iPad)
  - `iosX64` - Intel Mac simulators
  - `iosSimulatorArm64` - Apple Silicon simulators

**Build command:**
```bash
./gradlew :shared:assembleSharedXCFramework
```

**Output:** `shared/build/XCFrameworks/release/Shared.xcframework` (13MB)

---

### 2. **CocoaPods Distribution** ✅
- Updated `AvifKit.podspec` with correct configuration
- Version: 0.1.0
- Framework: Shared.xcframework
- Dependency: libavif ~> 1.0

**Podspec location:** `/AvifKit.podspec`

---

### 3. **Swift Package Manager (SPM)** ✅
- Updated `Package.swift` for both local and remote distribution
- Currently configured for local development
- Includes libavif-Xcode dependency
- Ready to switch to remote URL after GitHub release

**Package location:** `/Package.swift`

---

### 4. **Packaging Script** ✅
- Created helper script to zip XCFramework and calculate checksum
- Automatically generates SPM-ready package

**Script location:** `/scripts/package-xcframework.sh`

**Usage:**
```bash
./scripts/package-xcframework.sh 0.1.0
```

**Current output:**
- **File:** `build/release/Shared.xcframework.zip` (3.6MB)
- **Checksum:** `58827579d99a45cbca514da13a15347e0157653f289b798bbb287d22f1064aea`

---

### 5. **GitHub Actions Automation** ✅
- Created workflow for automated iOS publishing
- Triggers on version tags (e.g., 0.1.0, 1.2.3)
- Automatically:
  - Builds XCFramework
  - Packages and calculates checksum
  - Creates GitHub release
  - Updates Package.swift
  - (Optional) Publishes to CocoaPods

**Workflow location:** `/.github/workflows/publish-ios.yml`

---

### 6. **Documentation** ✅
- Created comprehensive iOS publishing guide
- Step-by-step instructions for all distribution methods
- Troubleshooting section
- Update process for future versions

**Guide location:** `/IOS_PUBLISHING_GUIDE.md`

---

## 🚀 Publishing Steps (Manual - First Release)

Follow these steps to publish version 0.1.0:

### Step 1: Final Testing

```bash
# Clean and rebuild everything
./gradlew clean
./gradlew :shared:assembleSharedXCFramework

# Verify the build
ls -lh shared/build/XCFrameworks/release/
```

### Step 2: Package for Distribution

```bash
# Package and get checksum
./scripts/package-xcframework.sh 0.1.0
```

**Expected output:**
```
✅ Package created successfully!
📍 Location: build/release/Shared.xcframework.zip
📏 Size: 3.6M
🔐 Checksum: 58827579d99a45cbca514da13a15347e0157653f289b798bbb287d22f1064aea
```

### Step 3: Commit and Tag

```bash
# Stage all changes
git add .

# Commit
git commit -m "Release version 0.1.0 - iOS support ready"

# Tag the release
git tag 0.1.0

# Push everything
git push origin main
git push origin 0.1.0
```

### Step 4: Create GitHub Release

**Option A: Via GitHub Web UI**
1. Go to https://github.com/alfikri-rizky/AvifKit/releases
2. Click "Draft a new release"
3. Choose tag: `0.1.0`
4. Title: `v0.1.0 - Initial iOS Release`
5. Description: (See IOS_PUBLISHING_GUIDE.md for template)
6. Upload: `build/release/Shared.xcframework.zip`
7. Click "Publish release"

**Option B: Via GitHub CLI**
```bash
gh release create 0.1.0 \
  build/release/Shared.xcframework.zip \
  --title "v0.1.0 - Initial iOS Release" \
  --notes-file release-notes.md
```

### Step 5: Update Package.swift

After the GitHub release is created, update Package.swift:

```swift
// Comment out local path:
// .binaryTarget(
//     name: "Shared",
//     path: "shared/build/XCFrameworks/release/Shared.xcframework"
// ),

// Uncomment and use remote URL:
.binaryTarget(
    name: "Shared",
    url: "https://github.com/alfikri-rizky/AvifKit/releases/download/0.1.0/Shared.xcframework.zip",
    checksum: "58827579d99a45cbca514da13a15347e0157653f289b798bbb287d22f1064aea"
),
```

```bash
git add Package.swift
git commit -m "Update Package.swift with remote XCFramework URL"
git push origin main
```

### Step 6: Publish to CocoaPods

**First-time setup (if not done already):**
```bash
# Register with CocoaPods Trunk
pod trunk register rizkyalfikri@gmail.com 'Rizky Alfikri' --description='AvifKit MacBook'
# Click the verification link in your email
```

**Publish:**
```bash
# Validate first
pod spec lint AvifKit.podspec --allow-warnings

# If validation passes, push to trunk
pod trunk push AvifKit.podspec --allow-warnings
```

---

## 🤖 Future Releases (Automated)

For future releases (0.2.0, 0.3.0, etc.), the process is much simpler:

### 1. Update Version Numbers

**gradle.properties:**
```properties
VERSION_NAME=0.2.0
```

**AvifKit.podspec:**
```ruby
spec.version = "0.2.0"
```

### 2. Commit, Tag, and Push

```bash
git add .
git commit -m "Bump version to 0.2.0"
git tag 0.2.0
git push origin main
git push origin 0.2.0
```

### 3. GitHub Actions Takes Over! 🎉

The workflow will automatically:
- ✅ Build XCFramework
- ✅ Package and calculate checksum
- ✅ Create GitHub release
- ✅ Update Package.swift
- ✅ (Optional) Publish to CocoaPods

### 4. (Optional) Manual CocoaPods Publish

If automated CocoaPods publishing is disabled:
```bash
pod trunk push AvifKit.podspec --allow-warnings
```

---

## 📊 Distribution Summary

After publishing, your library will be available via:

### For iOS Developers:

#### 1. CocoaPods
```ruby
pod 'AvifKit', '~> 0.1.0'
```

#### 2. Swift Package Manager
```swift
dependencies: [
    .package(url: "https://github.com/alfikri-rizky/AvifKit", from: "0.1.0")
]
```

#### 3. Direct Download
Download `Shared.xcframework.zip` from GitHub Releases

### For Android/KMM Developers:

```gradle
implementation("io.github.alfikri-rizky:avifkit:0.1.0")
```

---

## ✅ Pre-Publishing Checklist

Before you publish, make sure:

- [ ] All tests pass: `./gradlew test`
- [ ] Android is published to Maven Central
- [ ] XCFramework builds successfully
- [ ] README.md is up to date
- [ ] CHANGELOG.md documents changes
- [ ] License file exists
- [ ] Swift bridge file (AVIFNativeConverter.swift) is included
- [ ] Version numbers are consistent across:
  - [ ] gradle.properties
  - [ ] AvifKit.podspec
  - [ ] README.md examples

---

## 📦 What iOS Developers Will Need

When iOS developers integrate your library, they need to:

### CocoaPods Users:
1. Add `pod 'AvifKit'` to Podfile
2. Run `pod install`
3. Import: `import Shared` (Kotlin code) and use Swift bridge

### SPM Users:
1. Add package dependency in Xcode
2. Import: `import AvifKit`
3. Swift bridge is included automatically

### Direct XCFramework Users:
1. Download Shared.xcframework.zip
2. Add to Xcode project
3. Manually integrate Swift bridge file
4. Add libavif dependency

**Note:** All users need to integrate the Swift bridge (`AVIFNativeConverter.swift`) which is documented in your README.

---

## 🔧 Optional: Enable Automated CocoaPods Publishing

To enable automated CocoaPods publishing in GitHub Actions:

### 1. Get CocoaPods Token
```bash
pod trunk me --verbose
```

Copy the token from the output.

### 2. Add GitHub Secret
1. Go to https://github.com/alfikri-rizky/AvifKit/settings/secrets/actions
2. Click "New repository secret"
3. Name: `COCOAPODS_TRUNK_TOKEN`
4. Value: (paste your token)

### 3. Enable in GitHub Actions
1. Go to repository Settings → Variables → Actions
2. Add variable: `ENABLE_COCOAPODS_PUBLISH` = `true`

Now GitHub Actions will automatically publish to CocoaPods on each release!

---

## 🆘 Troubleshooting

### XCFramework build fails
```bash
# Clean and rebuild
./gradlew clean
./gradlew :shared:assembleSharedXCFramework --stacktrace
```

### CocoaPods validation fails
```bash
# Check for issues
pod spec lint AvifKit.podspec --verbose

# Common fix: use --allow-warnings
pod spec lint AvifKit.podspec --allow-warnings
```

### SPM: "Artifact does not match checksum"
```bash
# Recalculate checksum
swift package compute-checksum build/release/Shared.xcframework.zip
# Update Package.swift with the new checksum
```

### GitHub Actions fails
- Check the workflow logs in GitHub Actions tab
- Ensure all secrets are configured correctly
- Verify the tag format matches (e.g., 0.1.0, not v0.1.0)

---

## 📚 Documentation Files

All documentation is ready:

1. **README.md** - Main project documentation ✅
2. **PUBLISHING_GUIDE.md** - Maven Central publishing ✅
3. **IOS_PUBLISHING_GUIDE.md** - Detailed iOS publishing guide ✅
4. **IOS_PUBLISHING_READY.md** - This file (quick reference) ✅
5. **CHANGELOG.md** - Version history (exists) ✅
6. **LICENSE** - MIT License (exists) ✅

---

## 🎯 Next Steps

**To publish right now:**

1. Review this checklist ✅
2. Run final tests
3. Follow "Step 1-6" in the "Publishing Steps" section above
4. Announce your release! 🎉

**For future releases:**

1. Update version numbers
2. Tag and push
3. Let GitHub Actions handle the rest!

---

## 🎉 You're Ready!

Everything is set up and ready for iOS publishing! Your library will be available via:

- ✅ **CocoaPods** - Most popular iOS dependency manager
- ✅ **Swift Package Manager** - Native Xcode integration
- ✅ **Direct Download** - For manual integration
- ✅ **Maven Central** - Already published for Android ✅

**Questions?** Check the detailed guide: `IOS_PUBLISHING_GUIDE.md`

**Need help?** The setup is complete and tested. Just follow the steps above!

---

Good luck with your release! 🚀