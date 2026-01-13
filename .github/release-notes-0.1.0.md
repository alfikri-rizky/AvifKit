## AvifKit v0.1.0 - Initial iOS Release 🎉

### 📦 Installation

#### CocoaPods
```ruby
pod 'AvifKit', '~> 0.1.0'
```

#### Swift Package Manager
```swift
dependencies: [
    .package(url: "https://github.com/alfikri-rizky/AvifKit", from: "0.1.0")
]
```

#### Maven Central (Android/KMM)
```gradle
implementation("io.github.alfikri-rizky:avifkit:0.1.0")
```

### ✨ Features
- 🖼️ **AVIF Encoding** for iOS and Android
- 🎯 **Adaptive Compression Strategies** (SMART/STRICT)
- ⚡ **Quality Presets** (SPEED/BALANCED/QUALITY/STORAGE)
- 📱 **Native Performance** using libavif
- 🔄 **Kotlin Multiplatform** shared code across platforms

### 🎯 Compression Strategies
- **SMART**: Binary search for highest quality within target size (6-8 attempts)
- **STRICT**: Exhaustive search for smallest possible size (up to 10 attempts)

### ⚙️ Customizable Parameters
- Quality (0-100)
- Encoding speed (0-10)
- Chroma subsampling (YUV444/422/420)
- Max dimensions
- Target file size with adaptive compression
- Alpha channel quality
- Lossless mode
- Metadata preservation

### 📊 Platform Support
- **iOS**: 13.0+
- **Android**: SDK 24+ (Android 7.0)

### 🔐 XCFramework Checksum
```
8bad9c562a2e76a7f5ce9af40fa0a7a44f8c02f81313a81c2210cab7e9e571fe
```

### 📚 Documentation
- [README](https://github.com/alfikri-rizky/AvifKit/blob/main/README.md)
- [iOS Publishing Guide](https://github.com/alfikri-rizky/AvifKit/blob/main/IOS_PUBLISHING_GUIDE.md)
- [Swift Integration Setup](https://github.com/alfikri-rizky/AvifKit/blob/main/SWIFT_INTEGRATION_SETUP.md)
- [Maven Central](https://central.sonatype.com/artifact/io.github.alfikri-rizky/avifkit/0.1.0)

### 🔗 Links
- **GitHub**: https://github.com/alfikri-rizky/AvifKit
- **Issues**: https://github.com/alfikri-rizky/AvifKit/issues
- **Maven Central**: https://central.sonatype.com/artifact/io.github.alfikri-rizky/avifkit

### 📝 Notes
This is the initial public release of AvifKit with full iOS support via CocoaPods, Swift Package Manager, and direct XCFramework distribution.

For iOS integration, please refer to the [Swift Integration Setup Guide](https://github.com/alfikri-rizky/AvifKit/blob/main/SWIFT_INTEGRATION_SETUP.md) for details on adding the AVIFNativeConverter bridge.