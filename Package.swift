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
            targets: ["AvifKit", "Shared"]
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

        // Kotlin Multiplatform XCFramework
        // For published releases: use remote URL from GitHub Release
        .binaryTarget(
            name: "Shared",
            url: "https://github.com/alfikri-rizky/AvifKit/releases/download/v0.2.2/Shared.xcframework.zip",
            checksum: "CHECKSUM_PLACEHOLDER_UPDATE_AFTER_BUILD"
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
