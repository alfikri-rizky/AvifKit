#!/bin/bash

# Download pre-built libavif XCFramework for iOS
# This script downloads the official libavif-Xcode build from SDWebImage

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
SHARED_DIR="$PROJECT_ROOT/shared"
IOS_FRAMEWORKS_DIR="$SHARED_DIR/src/iosMain/frameworks"

# libavif-Xcode repository and version
LIBAVIF_REPO="https://github.com/SDWebImage/libavif-Xcode"
LIBAVIF_VERSION="1.1.1"  # Latest stable version
DOWNLOAD_URL="https://github.com/SDWebImage/libavif-Xcode/releases/download/${LIBAVIF_VERSION}/libavif.xcframework.zip"

echo "========================================="
echo "AvifKit - Download libavif XCFramework"
echo "========================================="
echo ""
echo "Version: ${LIBAVIF_VERSION}"
echo "Source: ${LIBAVIF_REPO}"
echo ""

# Create frameworks directory if it doesn't exist
mkdir -p "$IOS_FRAMEWORKS_DIR"

# Check if XCFramework already exists
if [ -d "$IOS_FRAMEWORKS_DIR/libavif.xcframework" ]; then
    echo "⚠️  libavif.xcframework already exists"
    read -p "Do you want to re-download? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "✅ Using existing XCFramework"
        exit 0
    fi
    rm -rf "$IOS_FRAMEWORKS_DIR/libavif.xcframework"
fi

# Download XCFramework
echo "📥 Downloading libavif XCFramework..."
cd "$IOS_FRAMEWORKS_DIR"

if command -v curl &> /dev/null; then
    curl -L -o libavif.xcframework.zip "$DOWNLOAD_URL"
elif command -v wget &> /dev/null; then
    wget -O libavif.xcframework.zip "$DOWNLOAD_URL"
else
    echo "❌ Error: curl or wget is required to download"
    exit 1
fi

# Extract XCFramework
echo "📦 Extracting XCFramework..."
unzip -q libavif.xcframework.zip
rm libavif.xcframework.zip

# Verify extraction
if [ -d "libavif.xcframework" ]; then
    echo "✅ libavif XCFramework downloaded successfully!"
    echo ""
    echo "Location: $IOS_FRAMEWORKS_DIR/libavif.xcframework"
    echo ""

    # Display framework info
    echo "Framework contents:"
    ls -la libavif.xcframework/
    echo ""

    # Create marker file
    echo "${LIBAVIF_VERSION}" > libavif.xcframework/.version

    echo "✅ Setup complete!"
    echo ""
    echo "Next steps:"
    echo "1. The XCFramework is now bundled with your KMP shared module"
    echo "2. When you publish, it will be included in your CocoaPods podspec"
    echo "3. Run './gradlew :shared:linkDebugFrameworkIosSimulatorArm64' to test"
else
    echo "❌ Error: Failed to extract XCFramework"
    exit 1
fi