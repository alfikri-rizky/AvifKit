// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "AvifKit",
    platforms: [
        .iOS(.v15),
        .macOS(.v12)
    ],
    products: [
        .library(
            name: "AvifKit",
            // Do NOT list "Shared" here. The AvifKit Swift target depends on
            // Shared (below) and re-exports its symbols via `@_exported import
            // Shared` in AvifKitExports.swift. Listing Shared as a sibling of
            // a target that already depends on it causes SPM to link the static
            // Shared.xcframework along two paths, producing two copies of the
            // Kotlin runtime in the consumer binary. Static-K/N globals (incl.
            // the AvifKitIos singleton) then split into two instances and the
            // Swift bridge's registration is invisible to consumer call sites.
            targets: ["AvifKit", "AvifKitObjC"]
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
            url: "https://github.com/alfikri-rizky/AvifKit/releases/download/v0.2.9/Shared.xcframework.zip",
            checksum: "c3d345094f77d3768efe3f5d02f7dc3a898eabbd0e351201ba96e0d199d789aa"
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
