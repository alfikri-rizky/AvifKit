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
        // For local development: use path below (commented out for published release)
        // .binaryTarget(
        //     name: "Shared",
        //         //     path: "shared/build/XCFrameworks/release/Shared.xcframework"
        //         // ),

        // For published releases: use remote URL from GitHub Release
        .binaryTarget(
            name: "Shared",
            url: "https://github.com/alfikri-rizky/AvifKit/releases/download/0.1.0/Shared.xcframework.zip",
            checksum: "361c16e92ea585d9c9b1b164095d9f898f909e20a90129c54a58e4f7c9a6738d"
        ),

        // Test target
        .testTarget(
            name: "AvifKitTests",
            dependencies: ["AvifKit"],
            path: "shared/src/iosTest/swift"
        )
    ]
)
