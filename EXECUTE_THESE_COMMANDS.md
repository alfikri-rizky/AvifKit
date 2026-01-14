# 🚀 Execute These Commands to Publish iOS

Everything is prepared! Just follow these steps to publish AvifKit v0.1.0 for iOS.

---

## ✅ What I've Already Done

- [x] Cleaned and rebuilt XCFramework (13MB)
- [x] Packaged for SPM distribution (3.6MB zip)
- [x] Calculated checksum: `8bad9c562a2e76a7f5ce9af40fa0a7a44f8c02f81313a81c2210cab7e9e571fe`
- [x] Created release notes template
- [x] Prepared all configuration files

---

## 🎯 What You Need to Do (4 Steps)

### Step 1: Review and Push to GitHub (1 minute)

```bash
# Check what will be committed
git status

# Review the changes
git diff

# If everything looks good, stage all changes
git add .

# Commit with descriptive message
git commit -m "Release v0.1.0 - iOS support via CocoaPods, SPM, and XCFramework

- Built and packaged XCFramework (13MB uncompressed, 3.6MB compressed)
- Configured CocoaPods podspec
- Set up Swift Package Manager support
- Created GitHub Actions workflow for automated releases
- Added comprehensive iOS publishing documentation"

# Push to main branch
git push origin main

# Create and push tag
git tag 0.1.0
git push origin 0.1.0
```

**Expected output**: Tag pushed successfully ✅

---

### Step 2: Create GitHub Release (3-5 minutes)

#### Option A: Via GitHub Web UI (Recommended)

1. Go to: **https://github.com/alfikri-rizky/AvifKit/releases**

2. Click **"Draft a new release"**

3. Fill in:
   - **Choose a tag**: Select `0.1.0` (the tag you just pushed)
   - **Release title**: `v0.1.0 - Initial iOS Release`
   - **Description**: Copy the content from `.github/release-notes-0.1.0.md`

4. **Attach the binary**:
   - Click "Attach binaries by dropping them here or selecting them"
   - Upload: `build/release/Shared.xcframework.zip`

5. Click **"Publish release"**

#### Option B: Via GitHub CLI (Faster if you have gh installed)

```bash
gh release create 0.1.0 \
  build/release/Shared.xcframework.zip \
  --title "v0.1.0 - Initial iOS Release" \
  --notes-file .github/release-notes-0.1.0.md
```

**Expected output**: Release created at https://github.com/alfikri-rizky/AvifKit/releases/tag/0.1.0 ✅

---

### Step 3: Update Package.swift (After Release Exists)

**After the GitHub release is created**, run this command to tell me it's done:

```
Just type: "Release 0.1.0 created"
```

**Then I will automatically**:
- Update Package.swift with the remote URL
- Add the correct checksum
- Prepare the commit for you to push

---

### Step 4: Publish to CocoaPods (2-10 minutes)

#### First-Time Setup (if you haven't registered with CocoaPods)

```bash
# Register with CocoaPods Trunk
pod trunk register rizkyalfikri@gmail.com 'Rizky Alfikri' --description='AvifKit MacBook'

# Check your email and click the verification link
# Then verify registration:
pod trunk me
```

#### Publish to CocoaPods

```bash
# Validate the podspec first
pod spec lint AvifKit.podspec --allow-warnings

# If validation passes, publish!
pod trunk push AvifKit.podspec --allow-warnings
```

**Expected output**:
```
🎉  Congrats

 🚀  AvifKit (0.1.0) successfully published
 📅  January 12th, 2026
 🌎  https://cocoapods.org/pods/AvifKit
 👍  Tell your friends!
```

---

## 📦 Files Ready for Upload

| File | Location | Size | Purpose |
|------|----------|------|---------|
| XCFramework Zip | `build/release/Shared.xcframework.zip` | 3.6MB | Upload to GitHub Release |
| Release Notes | `.github/release-notes-0.1.0.md` | - | Copy to GitHub Release description |

---

## 🔐 Important Information

**XCFramework Checksum** (for SPM):
```
8bad9c562a2e76a7f5ce9af40fa0a7a44f8c02f81313a81c2210cab7e9e571fe
```

**Download URL** (after release):
```
https://github.com/alfikri-rizky/AvifKit/releases/download/0.1.0/Shared.xcframework.zip
```

---

## ⚠️ Common Issues & Solutions

### Issue: "pod trunk push" fails with authentication error

**Solution**:
```bash
# Re-register
pod trunk register rizkyalfikri@gmail.com 'Rizky Alfikri'
# Check your email and verify
```

### Issue: "pod spec lint" validation errors

**Solution**:
```bash
# Use --allow-warnings flag
pod spec lint AvifKit.podspec --allow-warnings --verbose

# Check the verbose output for specific issues
```

### Issue: GitHub CLI not installed

**Solution**: Use Option A (Web UI) for Step 2 instead.

### Issue: "Tag already exists"

**Solution**:
```bash
# If you need to recreate the tag:
git tag -d 0.1.0
git push origin :refs/tags/0.1.0
git tag 0.1.0
git push origin 0.1.0
```

---

## ✅ Verification Checklist

After completing all steps, verify:

- [ ] GitHub tag `0.1.0` exists
- [ ] GitHub release exists at https://github.com/alfikri-rizky/AvifKit/releases/tag/0.1.0
- [ ] `Shared.xcframework.zip` is attached to the release
- [ ] Package.swift updated with remote URL
- [ ] CocoaPods shows your pod: https://cocoapods.org/pods/AvifKit

---

## 🎉 After Publishing

Your library will be available via:

### For iOS Developers:
```ruby
# CocoaPods
pod 'AvifKit', '~> 0.1.0'
```

```swift
// Swift Package Manager
.package(url: "https://github.com/alfikri-rizky/AvifKit", from: "0.1.0")
```

### For Android/KMM Developers:
```gradle
implementation("io.github.alfikri-rizky:avifkit:0.1.0")
```

---

## 📣 Share Your Release!

Consider announcing on:
- Twitter/X
- Reddit (r/iOSProgramming, r/androiddev, r/Kotlin)
- Dev.to
- Medium
- LinkedIn
- Your blog

Example tweet:
```
🎉 Excited to announce AvifKit v0.1.0!

A Kotlin Multiplatform library for AVIF image conversion on iOS & Android 📱

✨ Features:
- Adaptive compression (SMART/STRICT)
- Quality presets
- Native performance

📦 Available via CocoaPods, SPM & Maven Central

https://github.com/alfikri-rizky/AvifKit
```

---

## 📞 Need Help?

If you encounter any issues:
1. Check the troubleshooting section above
2. Review `IOS_PUBLISHING_GUIDE.md` for detailed explanations
3. Open an issue if needed

---

## 🚀 Ready? Let's Publish!

**Start with Step 1 above** ⬆️

Total time needed: **~15 minutes**

Good luck! 🎉