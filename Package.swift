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
        // For local development and SNAPSHOT builds: use local path
        .binaryTarget(
            name: "Shared",
            path: "shared/build/XCFrameworks/release/Shared.xcframework"
            url: "https://github.com/alfikri-rizky/AvifKit/releases/download/v0.1.2/Shared.xcframework.zip",
            checksum: "7db27895250d99fbaefea3640949dec58f316208dfdeaae831f3f255ef842392"
        ),

        // For published releases: use remote URL from GitHub Release
        // Uncomment and update checksum when publishing v0.1.3
        // .binaryTarget(
        //     name: "Shared",
        //     url: "https://github.com/alfikri-rizky/AvifKit/releases/download/v0.1.3/Shared.xcframework.zip",
        //     checksum: "UPDATE_THIS_CHECKSUM_AFTER_BUILDING"
        // ),

        // Test target
        .testTarget(
            name: "AvifKitTests",
            dependencies: ["AvifKit"],
            path: "shared/src/iosTest/swift"
        )
    ]
)
