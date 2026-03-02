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
        // libavif XCFramework for SPM
        // Using SDWebImage's pre-built libavif XCFramework
        // Pin to 0.11.x — libavif 1.0.0 has breaking API changes
        // (minQuantizer/maxQuantizer removed, avifImageAllocatePlanes signature changed, etc.)
        .package(url: "https://github.com/SDWebImage/libavif-Xcode.git", "0.11.1"..<"1.0.0")
    ],
    targets: [
        // Swift wrapper for AVIF conversion
        .target(
            name: "AvifKit",
            dependencies: [
                "Shared",
                .product(name: "libavif", package: "libavif-Xcode")
            ],
            path: "shared/src/iosMain/swift",
            publicHeadersPath: nil
        ),

        // Kotlin Multiplatform XCFramework
        // For published releases: use remote URL from GitHub Release
        .binaryTarget(
            name: "Shared",
            url: "https://github.com/alfikri-rizky/AvifKit/releases/download/v0.2.0/Shared.xcframework.zip",
            checksum: "4f5326f21abc0e984e533b5bffebbf0ab4a507f5c50b348a4c83c96190e336b5"
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
