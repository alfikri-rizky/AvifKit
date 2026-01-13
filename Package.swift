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
        .package(url: "https://github.com/SDWebImage/libavif-Xcode.git", from: "1.0.0")
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
        // For local development: use path (current)
        // For published release: use url + checksum (uncomment below)
        .binaryTarget(
            name: "Shared",
            path: "shared/build/XCFrameworks/release/Shared.xcframework"
        ),
        // For published releases, replace the above with:
        // .binaryTarget(
        //     name: "Shared",
        //     url: "https://github.com/alfikri-rizky/AvifKit/releases/download/0.1.0/Shared.xcframework.zip",
        //     checksum: "REPLACE_WITH_ACTUAL_CHECKSUM"
        // ),

        // Test target
        .testTarget(
            name: "AvifKitTests",
            dependencies: ["AvifKit"],
            path: "shared/src/iosTest/swift"
        )
    ]
)
