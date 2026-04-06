// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "AvifKit",
    platforms: [
        .iOS(.v13)
    ],
    products: [
        .library(
            name: "AvifKit",
            targets: ["AvifKit"]
        )
    ],
    dependencies: [
        // Note: You'll need to find an SPM-compatible AVIF library
        // or build libavif manually
    ],
    targets: [
        .target(
            name: "AvifKit",
            dependencies: [],
            path: "src/iosMain/swift"
        )
    ]
)
