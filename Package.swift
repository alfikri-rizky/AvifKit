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
            targets: ["AvifKit"]
        )
    ],
    dependencies: [
        // libavif dependency
        // Note: You may need to use a custom libavif SPM package
        // Example: .package(url: "https://github.com/SDWebImage/libavif-Xcode.git", from: "1.0.0")
    ],
    targets: [
        .target(
            name: "AvifKit",
            dependencies: [],
            path: "shared/src/iosMain/swift",
            publicHeadersPath: nil,
            cSettings: [
                .define("HAVE_LIBAVIF", to: "1")
            ],
            swiftSettings: [
                .define("HAVE_LIBAVIF")
            ]
        ),
        .testTarget(
            name: "AvifKitTests",
            dependencies: ["AvifKit"],
            path: "shared/src/iosTest/swift"
        )
    ]
)
