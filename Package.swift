// swift-tools-version:5.9
import PackageDescription

// AvifKit for Swift Package Manager.
//
// As of the cinterop migration (see docs/IOS_CINTEROP_SOLUTION.md), the AVIF
// codec (libavif + aom) is linked directly into the Kotlin/Native framework via
// cinterop. There is no longer a Swift wrapper, avif.swift dependency, or
// handler-registration step: the published `Shared.xcframework` is fully
// self-contained, exactly like the Android `avifkit-native` .so.
//
// Consumers use the Kotlin API directly:
//
//     import Shared
//     let converter = AvifConverter()
//
// BREAKING: the previous `AvifKit` Swift product (AVIFNativeConverter +
// AvifKitSetup.registerNativeHandler) has been removed.
let package = Package(
    name: "AvifKit",
    platforms: [
        .iOS(.v15),
        .macOS(.v12)
    ],
    products: [
        .library(
            name: "AvifKit",
            targets: ["Shared"]
        )
    ],
    targets: [
        // Kotlin Multiplatform XCFramework (self-contained: includes libavif + aom).
        // For published releases: use remote URL from GitHub Release.
        .binaryTarget(
            name: "Shared",
            // URL + checksum are updated automatically by the iOS publish workflow on
            // tag push (these point at the previous release until then).
            url: "https://github.com/alfikri-rizky/AvifKit/releases/download/v0.3.2/Shared.xcframework.zip",
            checksum: "87e34e14061b4ad52c2c08ce6d5a805dfc0c5733f4faeb0475854fa6396dae67"
        )

        // For local development and SNAPSHOT builds: use local path
        // .binaryTarget(
        //     name: "Shared",
        //     path: "shared/build/XCFrameworks/release/Shared.xcframework"
        // ),
    ]
)
