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
        .package(url: "https://github.com/SDWebImage/libavif-Xcode.git", from: "0.11.1")
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
            publicHeadersPath: nil,
            swiftSettings: [
                .define("canImport(libavif)")
            ]
        ),

        // Kotlin Multiplatform XCFramework
        // For published releases: use remote URL from GitHub Release
        .binaryTarget(
            name: "Shared",
            url: "https://github.com/alfikri-rizky/AvifKit/releases/download/v0.1.5/Shared.xcframework.zip",
            checksum: "a600def48b0a28c23166be1d31c30fe2f504792ab98d815eaa9b5e9373fd4f64"
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
