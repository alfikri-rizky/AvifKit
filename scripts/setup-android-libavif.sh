#!/bin/bash

# AvifKit - Android libavif Integration Setup Script
# This script downloads and configures libavif for Android NDK

set -e  # Exit on error

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
CPP_DIR="$PROJECT_ROOT/shared/src/androidMain/cpp"
LIBAVIF_DIR="$CPP_DIR/libavif"

echo "========================================="
echo "AvifKit - libavif Setup for Android"
echo "========================================="
echo ""

# Check prerequisites
echo "Checking prerequisites..."

if ! command -v git &> /dev/null; then
    echo "❌ Error: git is not installed"
    exit 1
fi

if ! command -v cmake &> /dev/null; then
    echo "❌ Error: cmake is not installed"
    echo "Install with: brew install cmake (macOS) or apt-get install cmake (Linux)"
    exit 1
fi

echo "✅ Prerequisites satisfied"
echo ""

# Create cpp directory if it doesn't exist
mkdir -p "$CPP_DIR"
cd "$CPP_DIR"

# Download libavif if not already present
if [ -d "$LIBAVIF_DIR" ]; then
    echo "📦 libavif already exists at $LIBAVIF_DIR"
    read -p "Do you want to update it? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "Updating libavif..."
        cd "$LIBAVIF_DIR"
        git pull
        cd "$CPP_DIR"
    fi
else
    echo "📥 Downloading libavif..."
    git clone --depth 1 https://github.com/AOMediaCodec/libavif.git
    echo "✅ libavif downloaded"
fi

echo ""
echo "========================================="
echo "Configuration Summary"
echo "========================================="
echo "Project Root: $PROJECT_ROOT"
echo "CPP Directory: $CPP_DIR"
echo "libavif Location: $LIBAVIF_DIR"
echo ""

# Create a flag file to indicate libavif is ready
touch "$CPP_DIR/.libavif-ready"

echo "✅ Setup complete!"
echo ""
echo "Next steps:"
echo "1. Uncomment NDK configuration in shared/build.gradle.kts"
echo "2. Build the project: ./gradlew :shared:build"
echo "3. The native library will be compiled with libavif support"
echo ""
echo "For detailed instructions, see INTEGRATION_GUIDE.md"
