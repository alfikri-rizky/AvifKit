# IOS APP - SWIFTUI DEMO APPLICATION

## OVERVIEW

SwiftUI demo app showcasing AvifKit library. NOT published - development/testing only.

## STRUCTURE

```
iosApp/
├── iosApp/
│   ├── iOSApp.swift           # @main entry point
│   ├── ContentView.swift      # Root navigation
│   ├── ViewModels/
│   │   └── AvifConverterViewModel.swift
│   ├── Views/
│   │   ├── UploadView.swift
│   │   ├── ResultView.swift
│   │   ├── QualitySelectorView.swift
│   │   └── CustomParametersView.swift
│   └── Models/
│       └── Models.swift
└── Native/
    └── AVIFNativeConverter.swift  # Bridge to libavif
```

## KOTLIN-SWIFT INTEROP

```swift
import Shared  // KMP XCFramework

// Register native handler at app startup (required!)
AvifKitSetup.registerNativeHandler()

// Then use Kotlin classes
let converter = AvifConverter()
```

## NATIVE HANDLER ARCHITECTURE

Kotlin cannot call Swift classes directly. The bridge pattern:
1. `IosAvifNativeHandler` (Kotlin protocol) defines encode/decode interface
2. `AvifKitNativeHandler` (Swift) implements it using libavif
3. `AvifKitSetup.registerNativeHandler()` connects them at startup
4. `AvifConverter.ios.kt` calls through `AvifKitIos.getHandler()`

**Two locations** - keep in sync:
1. `iosApp/Native/AVIFNativeConverter.swift` - Demo app copy
2. `shared/src/iosMain/swift/AVIFNativeConverter.swift` - Published library

## LIBAVIF AVAILABILITY CHECK

```swift
#if canImport(libavif)
import libavif
// Use native AVIF encoding
#else
// JPEG fallback
#endif
```

## VIEWMODEL PATTERN

```swift
@StateObject private var viewModel = AvifConverterViewModel()

// States
enum UiState {
    case idle
    case loading
    case success(result: ConversionResult, originalData: Data)
    case error(message: String)
}
```

## XCODE SETUP

1. Open `iosApp/iosApp.xcodeproj`
2. SPM resolves `Shared.xcframework` from GitHub Releases
3. libavif resolved via SDWebImage/libavif-Xcode package
4. Clean build (Cmd+Shift+K) required after package updates

## TROUBLESHOOTING

| Issue | Solution |
|-------|----------|
| "libavif not available" | Clean SPM cache + rebuild |
| XCFramework not found | Check Package.swift checksum |
| Simulator crashes | Use arm64 simulator, not x86_64 |
| Build errors after update | File > Packages > Reset Package Caches |
