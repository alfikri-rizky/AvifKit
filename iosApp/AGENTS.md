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
    └── iosApp-Bridging-Header.h   # Foundation/UIKit bridging (no AVIF code)
```

## KOTLIN INTEROP

```swift
import Shared  // KMP XCFramework — self-contained (includes libavif + aom)

// No registration step. Just use the Kotlin API directly.
let converter = AvifConverter()
```

## NATIVE ARCHITECTURE (v0.3.0+)

There is no Swift bridge or handler. `AvifConverter.ios.kt` calls `libavif` directly
via Kotlin/Native cinterop (`shared/src/nativeInterop/cinterop/libavif.def`); the codec
static libs are linked into `Shared.framework`. See `docs/IOS_CINTEROP_SOLUTION.md`.
The demo app simply consumes the published (or locally embedded) `Shared` XCFramework —
no `AVIFNativeConverter.swift`, no `registerNativeHandler()`.

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
2. SPM resolves `Shared.xcframework` from GitHub Releases (self-contained: includes the codec)
3. Clean build (Cmd+Shift+K) required after package updates

## TROUBLESHOOTING

| Issue | Solution |
|-------|----------|
| Undefined `_avif*` symbols at link | Run `scripts/build-ios-libavif.sh`; the codec static libs must exist before assembling `Shared` |
| XCFramework not found | Check Package.swift checksum |
| Simulator crashes | Use arm64 simulator, not x86_64 |
| Build errors after update | File > Packages > Reset Package Caches |
