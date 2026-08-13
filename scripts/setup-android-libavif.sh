#!/bin/bash

# AvifKit - Android libavif Integration Setup Script
# This script downloads and configures libavif + libaom for Android NDK
#
# For CI: runs non-interactively, clones fresh copies
# For local dev: detects existing directories and offers to update

set -e  # Exit on error

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
CPP_DIR="$PROJECT_ROOT/shared-native/src/main/cpp"
LIBAVIF_DIR="$CPP_DIR/libavif"

# Pinned versions for reproducible builds
LIBAVIF_TAG="v1.4.2"

echo "========================================="
echo "AvifKit - libavif Setup for Android"
echo "========================================="
echo ""
echo "libavif: $LIBAVIF_TAG"
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
    # Non-interactive in CI (no TTY), interactive locally
    if [ -t 0 ]; then
        echo "📦 libavif already exists at $LIBAVIF_DIR"
        read -p "Do you want to re-clone it? (y/n) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            echo "Removing old libavif..."
            rm -rf "$LIBAVIF_DIR"
        else
            echo "Keeping existing libavif"
        fi
    else
        echo "📦 CI mode: removing old libavif for fresh clone"
        rm -rf "$LIBAVIF_DIR"
    fi
fi

if [ ! -d "$LIBAVIF_DIR" ]; then
    echo "📥 Downloading libavif $LIBAVIF_TAG..."
    git clone --depth 1 --branch "$LIBAVIF_TAG" https://github.com/AOMediaCodec/libavif.git
    echo "✅ libavif downloaded"
fi

# Read libaom version from LocalAom.cmake to stay in sync
LOCALAOM_CMAKE="$LIBAVIF_DIR/cmake/Modules/LocalAom.cmake"
LIBAOM_TAG=$(grep 'set(AVIF_AOM_GIT_TAG' "$LOCALAOM_CMAKE" | sed 's/.*set(AVIF_AOM_GIT_TAG \(.*\))/\1/')
echo "libaom version from LocalAom.cmake: $LIBAOM_TAG"

# Pre-clone libaom into ext/aom
# This provides the source code locally so CMake doesn't need to download from googlesource
# (the googlesource tarball endpoint is unreliable — returns HTTP 500/502)
AOM_DIR="$LIBAVIF_DIR/ext/aom"
if [ ! -d "$AOM_DIR" ]; then
    echo "📥 Downloading libaom $LIBAOM_TAG..."
    mkdir -p "$LIBAVIF_DIR/ext"
    git clone --depth 1 --branch "$LIBAOM_TAG" https://aomedia.googlesource.com/aom "$AOM_DIR"
    echo "✅ libaom downloaded"
else
    echo "📦 libaom already exists at $AOM_DIR"
fi

# Make libavif actually use the ext/aom cloned above.
#
# From 1.4.x LocalAom.cmake tries to do this itself, but sets
# FETCHCONTENT_SOURCE_DIR_AOM while declaring the content as `libaom`. CMake reads
# FETCHCONTENT_SOURCE_DIR_<uppercased declared name>, so the variable it sets is never read and
# FetchContent goes to the network anyway — every other Local*.cmake in that release gets the
# pairing right (LocalLibyuv sets ..._LIBYUV for `libyuv`), which is what makes this a typo rather
# than a convention. Correcting the name here is the whole patch.
if [ -f "$LOCALAOM_CMAKE" ]; then
    if grep -q 'FETCHCONTENT_SOURCE_DIR_AOM' "$LOCALAOM_CMAKE"; then
        echo "🔧 Fixing FETCHCONTENT_SOURCE_DIR_AOM → FETCHCONTENT_SOURCE_DIR_LIBAOM..."
        # No \b anywhere: BSD sed (macOS) does not know it, so a pattern carrying one matches
        # nothing and the patch reports success while changing not a byte. Plain substrings are
        # safe here — the corrected name does not contain the old one, so this is idempotent.
        # -i.bak (not -i '') so this works with both BSD (macOS) and GNU (Linux) sed.
        sed -i.bak 's|FETCHCONTENT_SOURCE_DIR_AOM|FETCHCONTENT_SOURCE_DIR_LIBAOM|g' "$LOCALAOM_CMAKE"
        rm -f "$LOCALAOM_CMAKE.bak"
        echo "✅ LocalAom.cmake patched"
    fi

    # Assert the end state rather than the edit. This passes whether we just patched it or a
    # future libavif ships it correct, and fails loudly if the mechanism is gone altogether —
    # the previous version of this block skipped silently on a pattern miss, which would have
    # meant a quiet fall back to cloning aom over the network on every configure.
    if ! grep -q 'FETCHCONTENT_SOURCE_DIR_LIBAOM' "$LOCALAOM_CMAKE"; then
        echo "❌ Error: $LOCALAOM_CMAKE no longer points FetchContent at ext/aom."
        echo "   libavif $LIBAVIF_TAG must have restructured LocalAom.cmake. Without this the"
        echo "   build clones libaom from googlesource on every CMake configure."
        exit 1
    fi
fi

echo ""
echo "========================================="
echo "Configuration Summary"
echo "========================================="
echo "Project Root:     $PROJECT_ROOT"
echo "CPP Directory:    $CPP_DIR"
echo "libavif Location: $LIBAVIF_DIR (tag: $LIBAVIF_TAG)"
echo "libaom Location:  $AOM_DIR (tag: $LIBAOM_TAG)"
echo ""

# Create a flag file to indicate libavif is ready
touch "$CPP_DIR/.libavif-ready"

echo "✅ Setup complete!"
echo ""
echo "Next steps:"
echo "1. Build the project: ./gradlew :shared:build"
echo "2. The native library will be compiled with libavif support"
echo ""
