# IOS APP - SWIFTUI SHELL FOR AVIF STUDIO

## OVERVIEW

A shell, not an app. Every screen is drawn by `:composeApp` — the same Compose Multiplatform code
the Android app runs. There is no SwiftUI UI here to keep in sync, and adding one would mean two
implementations of the same product. Not published.

## STRUCTURE

```
iosApp/
├── iosApp/
│   ├── iOSApp.swift           # @main + ComposeView. The only Swift file in the app.
│   ├── Info.plist             # usage strings + CFBundleDocumentTypes for public.avif
│   ├── Assets.xcassets        # app icon, accent colour
│   └── Preview Content/
├── Configuration/
│   └── Config.xcconfig        # PRODUCT_NAME, bundle id, versions (project-level base config)
└── Native/
    └── iosApp-Bridging-Header.h   # unused; SWIFT_OBJC_BRIDGING_HEADER is not set
```

Files are picked up by an Xcode 16 **synchronized folder** (`PBXFileSystemSynchronizedRootGroup`),
so adding or deleting a Swift file under `iosApp/iosApp/` needs no pbxproj edit at all.

## HOW THE KOTLIN GETS IN

```swift
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ vc: UIViewController, context: Context) {}
}
```

The framework comes from `:composeApp`, not `:shared`. `:composeApp` links `:shared` statically, so
exactly one Kotlin framework is embedded — linking both would duplicate the Kotlin runtime.

Three pbxproj settings make that work, and all three are load-bearing:

| Setting | Value |
|---|---|
| Run Script phase (before Sources) | `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` |
| `FRAMEWORK_SEARCH_PATHS` | `$(SRCROOT)/../composeApp/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)` |
| `OTHER_LDFLAGS` | `-framework ComposeApp` |

`ENABLE_USER_SCRIPT_SANDBOXING` must stay `NO` — the Gradle script phase writes outside the
sandbox and KGP fails the build otherwise.

## SAFE AREA

`ComposeView` uses `.ignoresSafeArea()` on **all** edges, deliberately. Compose applies the insets
itself (the top bar consumes the status bar, the bottom bar consumes the home indicator). Letting
SwiftUI inset the host as well leaves a strip of window background under the bottom bar and
double-pads the top.

## OPENING FILES

`Info.plist` declares `CFBundleDocumentTypes` for `public.avif` (a system-declared UTI — no
`UTImportedTypeDeclarations` needed) at `LSHandlerRank = Alternate`. Incoming URLs arrive through
SwiftUI's `.onOpenURL` and are handed to `MainViewControllerKt.handleIncomingUrl(url:)`, which puts
them on the shared `IncomingFiles` flow the Compose UI collects.

## BUILD

```shell
# Kotlin side only — fastest way to check the framework still links
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64

# The app target. A green Gradle build says nothing about whether this links.
DEVICE_ID=$(xcrun simctl list devices available | grep -Eo '\(([0-9A-F-]{36})\)' | tr -d '()' | head -1)
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination "platform=iOS Simulator,id=$DEVICE_ID" build
```

The `iosApp` scheme is shared (`iosApp.xcodeproj/xcshareddata/xcschemes/`) so a clean checkout and
CI can both find it — a user scheme under `xcuserdata/` is git-ignored and would not survive.

## TROUBLESHOOTING

| Issue | Cause |
|-------|-------|
| `No such module 'ComposeApp'` | `FRAMEWORK_SEARCH_PATHS` unset, or the framework was never built |
| Undefined `_avif*` symbols at link | Run `scripts/build-ios-libavif.sh`; the codec static libs must exist before the Kotlin framework is assembled |
| Gradle phase fails with a sandbox error | `ENABLE_USER_SCRIPT_SANDBOXING` got flipped back to `YES` |
| Strip of background under the bottom bar | Something re-introduced a SwiftUI safe-area inset on `ComposeView` |
| UI changes do not show up | The change belongs in `:composeApp`; nothing here renders content |
