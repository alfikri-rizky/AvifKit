#!/bin/bash

# Script to package XCFramework for SPM distribution
# Usage: ./scripts/package-xcframework.sh [version]

set -e

VERSION=${1:-"0.1.0"}
XCFRAMEWORK_PATH="shared/build/XCFrameworks/release/Shared.xcframework"
OUTPUT_DIR="build/release"
ZIP_NAME="Shared.xcframework.zip"

echo "📦 Packaging AvifKit XCFramework v$VERSION"
echo "============================================"

# Check if XCFramework exists
if [ ! -d "$XCFRAMEWORK_PATH" ]; then
    echo "❌ XCFramework not found at $XCFRAMEWORK_PATH"
    echo "Build it first with: ./gradlew :shared:assembleSharedXCFramework"
    exit 1
fi

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Remove old zip if exists
if [ -f "$OUTPUT_DIR/$ZIP_NAME" ]; then
    echo "🗑️  Removing old zip file..."
    rm "$OUTPUT_DIR/$ZIP_NAME"
fi

# Zip the XCFramework
echo "🗜️  Compressing XCFramework..."
cd shared/build/XCFrameworks/release
zip -r "../../../../$OUTPUT_DIR/$ZIP_NAME" Shared.xcframework
cd ../../../../

# Calculate checksum
echo "🔐 Calculating checksum..."
CHECKSUM=$(swift package compute-checksum "$OUTPUT_DIR/$ZIP_NAME")

echo ""
echo "✅ Package created successfully!"
echo "================================"
echo "📍 Location: $OUTPUT_DIR/$ZIP_NAME"
echo "📏 Size: $(du -h "$OUTPUT_DIR/$ZIP_NAME" | cut -f1)"
echo "🔐 Checksum: $CHECKSUM"
echo ""
echo "📝 Next steps:"
echo "1. Upload $OUTPUT_DIR/$ZIP_NAME to GitHub Release v$VERSION"
echo "2. Update Package.swift with the checksum above"
echo "3. Update the URL to match the release tag"
echo ""
echo "Example URL:"
echo "https://github.com/alfikri-rizky/AvifKit/releases/download/$VERSION/$ZIP_NAME"