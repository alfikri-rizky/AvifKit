#!/bin/bash

# AvifKit - Prepare for Publishing Script
# This script prepares the library for publishing by building all native components
# Run this before publishing to Maven Central or creating a release

set -e  # Exit on error

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "========================================="
echo "AvifKit - Prepare for Publishing"
echo "========================================="
echo ""
echo "This script will:"
echo "1. Download and setup libavif for Android"
echo "2. Build Android native libraries for all ABIs"
echo "3. Build iOS XCFramework"
echo "4. Verify all components are ready"
echo ""
read -p "Continue? (y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Aborted."
    exit 0
fi

# Step 1: Setup Android libavif
echo ""
echo "========================================="
echo "Step 1: Setting up Android libavif"
echo "========================================="
cd "$PROJECT_ROOT"

if [ ! -f "scripts/setup-android-libavif.sh" ]; then
    echo "❌ Error: setup-android-libavif.sh not found"
    exit 1
fi

chmod +x scripts/setup-android-libavif.sh
./scripts/setup-android-libavif.sh

# Step 2: Build Android native libraries
echo ""
echo "========================================="
echo "Step 2: Building Android native libraries"
echo "========================================="

if ! command -v ./gradlew &> /dev/null; then
    echo "❌ Error: gradlew not found"
    exit 1
fi

echo "Building shared module (includes native libraries for all ABIs)..."
./gradlew :shared:build --console=plain

if [ $? -eq 0 ]; then
    echo "✅ Android build successful"
else
    echo "❌ Android build failed"
    exit 1
fi

# Verify native libraries were built
NATIVE_LIBS_DIR="$PROJECT_ROOT/shared/build/intermediates/cmake"
if [ -d "$NATIVE_LIBS_DIR" ]; then
    echo ""
    echo "Native libraries built for:"
    find "$NATIVE_LIBS_DIR" -name "*.so" -type f | while read -r lib; do
        echo "  - $(basename $(dirname $(dirname $lib)))/$(basename $lib)"
    done
else
    echo "⚠️  Warning: Native libraries directory not found"
fi

# Step 3: Build iOS XCFramework
echo ""
echo "========================================="
echo "Step 3: Building iOS XCFramework"
echo "========================================="

echo "Building XCFramework for iOS..."
./gradlew :shared:assembleSharedXCFramework --console=plain

if [ $? -eq 0 ]; then
    echo "✅ iOS XCFramework build successful"
else
    echo "❌ iOS XCFramework build failed"
    exit 1
fi

# Verify XCFramework was created
XCFRAMEWORK_DIR="$PROJECT_ROOT/shared/build/XCFrameworks"
if [ -d "$XCFRAMEWORK_DIR" ]; then
    echo ""
    echo "XCFrameworks created:"
    find "$XCFRAMEWORK_DIR" -name "*.xcframework" -type d -maxdepth 2 | while read -r fw; do
        echo "  - $(basename $fw)"
    done
else
    echo "⚠️  Warning: XCFramework directory not found"
fi

# Step 4: Final verification
echo ""
echo "========================================="
echo "Step 4: Final Verification"
echo "========================================="

# Check if AAR was created
AAR_DIR="$PROJECT_ROOT/shared/build/outputs/aar"
if [ -d "$AAR_DIR" ] && [ -n "$(ls -A $AAR_DIR/*.aar 2>/dev/null)" ]; then
    echo "✅ Android AAR found:"
    ls -lh "$AAR_DIR"/*.aar | awk '{print "   " $9 " (" $5 ")"}'
else
    echo "⚠️  Warning: Android AAR not found"
fi

# Check if publications are configured
echo ""
echo "Checking Maven publication configuration..."
./gradlew :shared:tasks --group=publishing | grep -q "publish"
if [ $? -eq 0 ]; then
    echo "✅ Maven publishing tasks available"
    echo ""
    echo "Available publishing tasks:"
    ./gradlew :shared:tasks --group=publishing | grep "publish" | sed 's/^/   /'
else
    echo "⚠️  Warning: No publishing tasks found"
fi

echo ""
echo "========================================="
echo "✅ Preparation Complete!"
echo "========================================="
echo ""
echo "Your library is now ready for publishing!"
echo ""
echo "Next steps:"
echo ""
echo "For Maven Central:"
echo "  ./gradlew :shared:publishAllPublicationsToSonatypeRepository"
echo ""
echo "For local Maven:"
echo "  ./gradlew :shared:publishToMavenLocal"
echo ""
echo "For CocoaPods:"
echo "  pod trunk push AvifKit.podspec"
echo ""
echo "For SPM:"
echo "  Commit and tag: git tag 1.0.0 && git push --tags"
echo ""
echo "For GitHub Release:"
echo "  Create a release with the tag and attach:"
echo "  - AAR file from: shared/build/outputs/aar/"
echo "  - XCFramework from: shared/build/XCFrameworks/"
echo ""