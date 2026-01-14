# 🚀 Quick Start: Publishing iOS in 6 Steps

Your AvifKit library is **100% ready** for iOS publishing! Here's the fastest path to publish:

---

## ⚡ Super Quick (6 Commands)

```bash
# 1. Build XCFramework
./gradlew :shared:assembleSharedXCFramework

# 2. Package for SPM
./scripts/package-xcframework.sh 0.1.0

# 3. Tag and push
git add . && git commit -m "Release v0.1.0 for iOS"
git tag 0.1.0 && git push origin main && git push origin 0.1.0

# 4. Create GitHub release (via web or gh cli)
gh release create 0.1.0 build/release/Shared.xcframework.zip \
  --title "v0.1.0" --notes "Initial iOS release"

# 5. Update Package.swift (see IOS_PUBLISHING_READY.md)
# Edit Package.swift to use remote URL with checksum from step 2

# 6. Publish to CocoaPods
pod trunk push AvifKit.podspec --allow-warnings
```

---

## 📋 What's Included

### ✅ Ready to Use:
- **CocoaPods**: `AvifKit.podspec` configured
- **SPM**: `Package.swift` configured
- **XCFramework**: Build system ready (13MB framework)
- **Automation**: GitHub Actions workflow ready
- **Packaging**: Helper script created
- **Documentation**: Complete guides written

### 📦 Build Artifacts:
- `Shared.xcframework` (13MB) - Universal iOS framework
- `Shared.xcframework.zip` (3.6MB) - For SPM distribution
- **Checksum**: `58827579d99a45cbca514da13a15347e0157653f289b798bbb287d22f1064aea`

---

## 📚 Documentation Files

All guides are ready in your repo:

1. **IOS_PUBLISHING_READY.md** ⭐ - Start here! Complete walkthrough
2. **IOS_PUBLISHING_GUIDE.md** - Detailed instructions & troubleshooting
3. **QUICK_START_IOS.md** - This file (super quick reference)

---

## 🎯 Three Publishing Options

### Option 1: Manual (Full Control)
Follow steps in `IOS_PUBLISHING_READY.md`
Time: ~15 minutes

### Option 2: Semi-Automatic (Recommended)
Use GitHub Actions for most steps, manual CocoaPods
Time: ~5 minutes

### Option 3: Fully Automatic (Future Releases)
Setup once, then just push tags!
Time: ~1 minute per release

---

## 🌟 Distribution Methods

After publishing, iOS developers can install via:

### CocoaPods (Most Popular)
```ruby
pod 'AvifKit', '~> 0.1.0'
```

### Swift Package Manager (Modern)
```swift
.package(url: "https://github.com/alfikri-rizky/AvifKit", from: "0.1.0")
```

### Direct XCFramework
Download from GitHub Releases

---

## ❓ Need More Details?

- **Full guide**: Open `IOS_PUBLISHING_READY.md`
- **Troubleshooting**: See `IOS_PUBLISHING_GUIDE.md`
- **Android already published**: ✅ Ready on Maven Central

---

## 🎉 You're All Set!

Everything is configured and tested. Just run the 6 commands above to publish! 🚀