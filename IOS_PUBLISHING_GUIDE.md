# iOS Publishing Guide for AvifKit

This guide covers how to publish your KMM library for iOS developers via **CocoaPods**, **Swift Package Manager (SPM)**, and **Direct XCFramework** distribution.

---

## 📋 Prerequisites

- [x] Android already published to Maven Central ✅
- [x] XCFramework configuration added to build.gradle.kts ✅
- [x] Package.swift created for SPM ✅
- [x] AvifKit.podspec created for CocoaPods ✅
- [x] XCFramework build tested ✅

---

## 🚀 Publishing Steps

### Step 1: Build the XCFramework

```bash
# Build XCFramework for all iOS architectures
./gradlew :shared:assembleSharedXCFramework

# Package and get checksum for SPM
./scripts/package-xcframework.sh 0.1.0
```

**Output:**
- `shared/build/XCFrameworks/release/Shared.xcframework` (13MB)
- `build/release/Shared.xcframework.zip` (3.6MB) for SPM
- Checksum: `58827579d99a45cbca514da13a15347e0157653f289b798bbb287d22f1064aea`

---

### Step 2: Create GitHub Release

```bash
# Commit all changes
git add .
git commit -m "Release version 0.1.0 for iOS"

# Tag the release
git tag 0.1.0
git push origin main
git push origin 0.1.0
```

**On GitHub:**
1. Go to: https://github.com/alfikri-rizky/AvifKit/releases
2. Click "Create a new release"
3. Choose tag: `0.1.0`
4. Release title: `v0.1.0 - Initial iOS Release`
5. Description:
```markdown
## AvifKit v0.1.0 - iOS Support

### 📦 Distribution Options

#### CocoaPods
\`\`\`ruby
pod 'AvifKit', '~> 0.1.0'
\`\`\`

#### Swift Package Manager
\`\`\`swift
dependencies: [
    .package(url: "https://github.com/alfikri-rizky/AvifKit", from: "0.1.0")
]
\`\`\`

#### Maven Central (Android/KMM)
\`\`\`gradle
implementation("io.github.alfikri-rizky:avifkit:0.1.0")
\`\`\`

### ✨ Features
- 🖼️ AVIF encoding for iOS and Android
- 🎯 Adaptive compression strategies (SMART/STRICT)
- ⚡ Quality presets (SPEED/BALANCED/QUALITY/STORAGE)
- 📱 Native performance using libavif
- 🔄 Kotlin Multiplatform shared code

### 📚 Documentation
- [README](https://github.com/alfikri-rizky/AvifKit/blob/main/README.md)
- [iOS Integration Guide](https://github.com/alfikri-rizky/AvifKit/blob/main/SWIFT_INTEGRATION_SETUP.md)
- [Publishing Guide](https://github.com/alfikri-rizky/AvifKit/blob/main/PUBLISHING_GUIDE.md)

### 🔗 Links
- **Maven Central**: https://central.sonatype.com/artifact/io.github.alfikri-rizky/avifkit/0.1.0
- **CocoaPods**: https://cocoapods.org/pods/AvifKit
\`\`\`

6. **Attach file**: Upload `build/release/Shared.xcframework.zip`
7. Click "Publish release"

---

### Step 3: Update Package.swift for SPM

After creating the GitHub release, update Package.swift:

```swift
.binaryTarget(
    name: "Shared",
    url: "https://github.com/alfikri-rizky/AvifKit/releases/download/0.1.0/Shared.xcframework.zip",
    checksum: "58827579d99a45cbca514da13a15347e0157653f289b798bbb287d22f1064aea"
),
```

```bash
# Commit the change
git add Package.swift
git commit -m "Update Package.swift with remote XCFramework URL"
git push origin main
```

---

### Step 4: Publish to CocoaPods

#### First-time Setup (One-time only)

```bash
# Register with CocoaPods Trunk
pod trunk register rizkyalfikri@gmail.com 'Rizky Alfikri' --description='AvifKit MacBook'

# Check your email and click the verification link
```

#### Validate and Publish

```bash
# Validate the podspec
pod spec lint AvifKit.podspec --allow-warnings

# If validation passes, push to CocoaPods trunk
pod trunk push AvifKit.podspec --allow-warnings
```

**Expected output:**
```
🎉  Congrats

 🚀  AvifKit (0.1.0) successfully published
 📅  January 12th, 2026
 🌎  https://cocoapods.org/pods/AvifKit
 👍  Tell your friends!
```

---

## 📦 What iOS Developers Will Get

### Option 1: CocoaPods (Easiest)

**Installation:**
```ruby
# Podfile
pod 'AvifKit', '~> 0.1.0'
```

**What it includes:**
- Shared.xcframework (Kotlin code)
- AVIFNativeConverter.swift (Swift bridge)
- libavif dependency (automatic)

### Option 2: Swift Package Manager (Modern)

**Installation:**
```swift
// Package.swift
dependencies: [
    .package(url: "https://github.com/alfikri-rizky/AvifKit", from: "0.1.0")
]
```

**What it includes:**
- Shared.xcframework from GitHub Release
- Swift bridge files
- libavif-Xcode dependency

### Option 3: Direct XCFramework (Manual)

**Installation:**
1. Download `Shared.xcframework.zip` from GitHub Release
2. Unzip and drag `Shared.xcframework` into Xcode project
3. Add to "Frameworks, Libraries, and Embedded Content"
4. Manually add libavif dependency

---

## 🔄 Update Process (Future Releases)

For version 0.2.0, 0.3.0, etc.:

### 1. Update Version Numbers

**gradle.properties:**
```properties
VERSION_NAME=0.2.0
```

**AvifKit.podspec:**
```ruby
spec.version = "0.2.0"
```

### 2. Build and Package

```bash
./gradlew :shared:assembleSharedXCFramework
./scripts/package-xcframework.sh 0.2.0
```

### 3. Create GitHub Release

```bash
git tag 0.2.0
git push origin 0.2.0
# Create release and upload Shared.xcframework.zip
```

### 4. Update Package.swift

Update the URL and checksum for the new version.

### 5. Publish to CocoaPods

```bash
pod trunk push AvifKit.podspec --allow-warnings
```

---

## 🤖 Automated Publishing with GitHub Actions

Create `.github/workflows/publish-ios.yml`:

```yaml
name: Publish iOS

on:
  push:
    tags:
      - '*.*.*'  # Trigger on version tags (e.g., 0.1.0)

jobs:
  publish-ios:
    runs-on: macos-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/gradle-build-action@v2

      - name: Extract version from tag
        id: get_version
        run: echo "VERSION=${GITHUB_REF#refs/tags/}" >> $GITHUB_OUTPUT

      - name: Build XCFramework
        run: ./gradlew :shared:assembleSharedXCFramework --no-daemon

      - name: Package XCFramework
        run: |
          chmod +x scripts/package-xcframework.sh
          ./scripts/package-xcframework.sh ${{ steps.get_version.outputs.VERSION }}

      - name: Calculate Checksum
        id: checksum
        run: |
          CHECKSUM=$(swift package compute-checksum build/release/Shared.xcframework.zip)
          echo "CHECKSUM=$CHECKSUM" >> $GITHUB_OUTPUT

      - name: Update Package.swift
        run: |
          sed -i '' 's|path:.*Shared\.xcframework"|url: "https://github.com/alfikri-rizky/AvifKit/releases/download/${{ steps.get_version.outputs.VERSION }}/Shared.xcframework.zip", checksum: "${{ steps.checksum.outputs.CHECKSUM }}"|' Package.swift
          git config user.name github-actions
          git config user.email github-actions@github.com
          git add Package.swift
          git commit -m "Update Package.swift for release ${{ steps.get_version.outputs.VERSION }}"
          git push

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v1
        with:
          files: build/release/Shared.xcframework.zip
          body: |
            ## AvifKit ${{ steps.get_version.outputs.VERSION }}

            ### Installation

            **CocoaPods:**
            \`\`\`ruby
            pod 'AvifKit', '~> ${{ steps.get_version.outputs.VERSION }}'
            \`\`\`

            **SPM:**
            \`\`\`swift
            .package(url: "https://github.com/alfikri-rizky/AvifKit", from: "${{ steps.get_version.outputs.VERSION }}")
            \`\`\`

            **Checksum:** ${{ steps.checksum.outputs.CHECKSUM }}
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

      - name: Publish to CocoaPods
        env:
          COCOAPODS_TRUNK_TOKEN: ${{ secrets.COCOAPODS_TRUNK_TOKEN }}
        run: |
          pod trunk push AvifKit.podspec --allow-warnings
```

**Setup GitHub Secrets:**
1. Get CocoaPods token: `pod trunk me --verbose`
2. Go to GitHub repo → Settings → Secrets → Actions
3. Add `COCOAPODS_TRUNK_TOKEN` with your token

---

## 🧪 Testing the Distribution

### Test CocoaPods Locally

```bash
# Create a new test iOS app
cd /tmp
pod lib lint AvifKit.podspec --allow-warnings
```

### Test SPM Locally

```bash
# In Xcode, create a new project
# File → Add Package Dependencies
# Enter: https://github.com/alfikri-rizky/AvifKit
# Select version 0.1.0
```

### Test Direct XCFramework

```bash
# Open test project in Xcode
# Drag Shared.xcframework into project
# Build and run
```

---

## 📝 Verification Checklist

After publishing, verify:

- [ ] GitHub Release created with tag 0.1.0
- [ ] `Shared.xcframework.zip` attached to release
- [ ] Package.swift updated with correct URL and checksum
- [ ] CocoaPods trunk push successful
- [ ] Pod available at https://cocoapods.org/pods/AvifKit
- [ ] SPM resolution works in Xcode
- [ ] Maven Central shows iOS artifacts:
  - `avifkit-iosarm64`
  - `avifkit-iosx64`
  - `avifkit-iossimulatorarm64`

---

## ⚠️ Common Issues

### CocoaPods: "Unable to find a specification"
```bash
# Clear CocoaPods cache
pod cache clean --all
pod repo update
```

### SPM: "Artifact does not match checksum"
```bash
# Recalculate checksum
swift package compute-checksum build/release/Shared.xcframework.zip
# Update Package.swift with new checksum
```

### XCFramework: "Framework not found"
- Ensure XCFramework is added to "Frameworks, Libraries, and Embedded Content"
- Check that "Embed & Sign" is selected

---

## 🔗 Useful Links

- **CocoaPods Guides**: https://guides.cocoapods.org/making/getting-setup-with-trunk.html
- **SPM Documentation**: https://swift.org/package-manager/
- **Kotlin Multiplatform**: https://kotlinlang.org/docs/multiplatform-publish-lib.html
- **XCFramework Guide**: https://developer.apple.com/documentation/xcode/creating-a-multi-platform-binary-framework-bundle

---

## 🎉 Success!

Your library is now available for iOS developers via:
1. ✅ CocoaPods: `pod 'AvifKit'`
2. ✅ SPM: `.package(url: "...")`
3. ✅ Direct Download from GitHub Releases

iOS developers can now integrate AvifKit into their projects easily! 🚀