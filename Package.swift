// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "AvifKit",
    platforms: [
        .iOS(.v13),
        .macOS(.v10_15)
    ],
    products: [
        .library(
            name: "AvifKit",
            targets: ["AvifKit", "AvifKitObjC", "Shared"]
        )
    ],
    dependencies: [
        // AVIF encoding/decoding via avif.swift
        // Uses aom for encoding and dav1d for fast decoding
        // Pre-built XCFramework dependencies — no source compilation needed
        .package(url: "https://github.com/awxkee/avif.swift.git", "2.1.0"..<"3.0.0")
    ],
    targets: [
        // Swift wrapper for AVIF conversion
        .target(
            name: "AvifKit",
            dependencies: [
                "Shared",
                .product(name: "avif", package: "avif.swift")
            ],
            path: "shared/src/iosMain/swift",
            publicHeadersPath: nil
        ),

        // Objective-C auto-registration (SPM forbids mixing Swift and ObjC in the same target folder)
        .target(
            name: "AvifKitObjC",
            path: "shared/src/iosMain/objc",
            publicHeadersPath: "include"
        ),

        // Kotlin Multiplatform XCFramework
        // For published releases: use remote URL from GitHub Release
        .binaryTarget(
            name: "Shared",
            url: "https://github.com/alfikri-rizky/AvifKit/releases/download/v0.2.7/Shared.xcframework.zip",
            checksum: "ee9a3622e2269451bbac08f2d4fcbdcc76b8a42278fd13443af8af715b07e4f0"
        ),

        // For local development and SNAPSHOT builds: use local path
        // .binaryTarget(
        //     name: "Shared",
        //     path: "shared/build/XCFrameworks/release/Shared.xcframework"
        // ),

        // Test target
        .testTarget(
            name: "AvifKitTests",
            dependencies: ["AvifKit"],
            path: "shared/src/iosTest/swift"
        )
    ]
)
