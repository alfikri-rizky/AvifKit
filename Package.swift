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
        // Pre-built from the shared module
        // Build with: ./gradlew :shared:assembleSharedXCFramework
        .binaryTarget(
            name: "Shared",
            path: "shared/build/XCFrameworks/release/Shared.xcframework"
        ),

        // Test target
        .testTarget(
            name: "AvifKitTests",
            dependencies: ["AvifKit"],
            path: "shared/src/iosTest/swift"
        )
    ]
)
