#!/bin/bash

# AvifKit - iOS avif.swift Integration Setup Script
# This script sets up avif.swift integration via CocoaPods or SPM

set -e  # Exit on error

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
IOS_DIR="$PROJECT_ROOT/iosApp"
SHARED_DIR="$PROJECT_ROOT/shared"

echo "========================================="
echo "AvifKit - avif.swift Setup for iOS"
echo "========================================="
echo ""

# Check prerequisites
echo "Checking prerequisites..."

if ! command -v git &> /dev/null; then
    echo "❌ Error: git is not installed"
    exit 1
fi

echo "✅ Prerequisites satisfied"
echo ""

# Check for CocoaPods or SPM preference
echo "Choose integration method:"
echo "1) CocoaPods (recommended for existing CocoaPods projects)"
echo "2) Swift Package Manager (SPM)"
echo ""
read -p "Enter choice (1 or 2): " choice

case $choice in
    1)
        echo ""
        echo "Setting up CocoaPods integration..."

        # Check if CocoaPods is installed
        if ! command -v pod &> /dev/null; then
            echo "⚠️  CocoaPods not installed"
            echo "Install with: sudo gem install cocoapods"
            read -p "Install now? (y/n) " -n 1 -r
            echo
            if [[ $REPLY =~ ^[Yy]$ ]]; then
                sudo gem install cocoapods
            else
                echo "❌ CocoaPods required for this option"
                exit 1
            fi
        fi

        # Create or update Podfile
        PODFILE="$IOS_DIR/Podfile"

        if [ ! -f "$PODFILE" ]; then
            echo "Creating Podfile..."
            cat > "$PODFILE" << 'EOF'
platform :ios, '13.0'

target 'iosApp' do
  use_frameworks!

  # AvifKit dependencies
  pod 'libavif'

  # Shared KMP framework
  pod 'Shared', :path => '../shared'
end
EOF
            echo "✅ Podfile created"
        else
            echo "⚠️  Podfile already exists"
            if ! grep -q "libavif" "$PODFILE"; then
                echo "Adding libavif to Podfile..."
                # Backup original
                cp "$PODFILE" "$PODFILE.backup"
                # Add libavif pod (this is a simple append, you may need to adjust manually)
                echo "  pod 'libavif'" >> "$PODFILE"
                echo "✅ libavif added to Podfile"
            else
                echo "✅ libavif already in Podfile"
            fi
        fi

        # Install pods
        echo ""
        echo "Installing CocoaPods dependencies..."
        cd "$IOS_DIR"
        pod install || pod repo update && pod install

        echo ""
        echo "✅ CocoaPods setup complete!"
        echo "   Open iosApp.xcworkspace (not .xcodeproj) in Xcode"
        ;;

    2)
        echo ""
        echo "Setting up Swift Package Manager..."

        # Create Package.swift if it doesn't exist
        PACKAGE_FILE="$SHARED_DIR/Package.swift"

        if [ ! -f "$PACKAGE_FILE" ]; then
            echo "Creating Package.swift..."
            cat > "$PACKAGE_FILE" << 'EOF'
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
EOF
            echo "✅ Package.swift created"
        else
            echo "✅ Package.swift already exists"
        fi

        echo ""
        echo "✅ SPM setup complete!"
        echo "   Note: You'll need to add avif.swift or libavif manually"
        echo "   See: https://github.com/SDWebImage/libavif-Xcode"
        ;;

    *)
        echo "❌ Invalid choice"
        exit 1
        ;;
esac

echo ""
echo "========================================="
echo "Next Steps:"
echo "========================================="
echo "1. Review the created configuration files"
echo "2. Build the iOS project in Xcode"
echo "3. The Swift bridge files are in: shared/src/iosMain/swift/"
echo ""
echo "For detailed instructions, see INTEGRATION_GUIDE.md"
echo ""
