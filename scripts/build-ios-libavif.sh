#!/bin/bash

# AvifKit - iOS libavif Static Library Build Script
# =================================================
# Builds libavif + libaom as static libraries (.a) for the three Kotlin/Native
# iOS targets so the Kotlin/Native klib can link the AVIF codec directly via
# cinterop — the iOS analog of the Android `avifkit-native` .so.
#
# This is the iOS counterpart of scripts/setup-android-libavif.sh + the
# :shared-native CMake build. It reuses the SAME libavif v1.2.1 source tree
# (with ext/aom prefetched) that Android already vendors, so both platforms stay
# on the same codec version.
#
# Output (one libavif.a + libaom.a per Kotlin/Native target name):
#   shared/src/nativeInterop/libs/ios/iosArm64/{libavif.a,libaom.a}
#   shared/src/nativeInterop/libs/ios/iosSimulatorArm64/{libavif.a,libaom.a}
#   shared/src/nativeInterop/libs/ios/iosX64/{libavif.a,libaom.a}
#
# Usage:
#   ./scripts/build-ios-libavif.sh                 # build all 3 targets
#   ./scripts/build-ios-libavif.sh iosSimulatorArm64   # build one target
#
# Requirements: Xcode + command line tools, CMake. (Ninja optional; falls back
# to the Unix Makefiles generator.)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Canonical libavif source: the same tree the Android :shared-native build uses
# (populated by scripts/setup-android-libavif.sh; has include/ + ext/aom).
LIBAVIF_DIR="$PROJECT_ROOT/shared-native/src/main/cpp/libavif"
# Out-of-tree build root (kept out of the shared source tree; git-ignored).
BUILD_ROOT="$PROJECT_ROOT/build/ios-libavif"
# Final per-target static-lib output consumed by the cinterop linkerOpts.
OUT_ROOT="$PROJECT_ROOT/shared/src/nativeInterop/libs/ios"

LIBAVIF_TAG="v1.2.1"
DEPLOYMENT_TARGET="15.0"

echo "========================================="
echo "AvifKit - iOS libavif static-lib build"
echo "========================================="
echo "libavif: $LIBAVIF_TAG"
echo "source:  $LIBAVIF_DIR"
echo ""

# --- Prerequisites -----------------------------------------------------------
command -v cmake >/dev/null 2>&1 || { echo "❌ cmake not found"; exit 1; }
command -v xcrun >/dev/null 2>&1 || { echo "❌ xcrun (Xcode CLT) not found"; exit 1; }

# Ensure the libavif source (shared with Android) is present; clone it if not.
if [ ! -f "$LIBAVIF_DIR/include/avif/avif.h" ] || [ ! -d "$LIBAVIF_DIR/ext/aom" ]; then
  echo "ℹ️  libavif source missing at $LIBAVIF_DIR — running setup-android-libavif.sh"
  chmod +x "$SCRIPT_DIR/setup-android-libavif.sh"
  "$SCRIPT_DIR/setup-android-libavif.sh"
fi
if [ ! -f "$LIBAVIF_DIR/include/avif/avif.h" ] || [ ! -d "$LIBAVIF_DIR/ext/aom" ]; then
  echo "❌ libavif source still not available at $LIBAVIF_DIR"
  exit 1
fi

# Pick a generator: prefer Ninja, fall back to Unix Makefiles.
if command -v ninja >/dev/null 2>&1; then
  GENERATOR="Ninja"
  BUILD_FLAGS=()
else
  GENERATOR="Unix Makefiles"
  BUILD_FLAGS=(-j"$(sysctl -n hw.ncpu)")
fi
echo "generator: $GENERATOR"
echo ""

# --- Map Kotlin/Native target name -> (SDK, arch) ----------------------------
# iosArm64            -> iphoneos      / arm64   (real devices)
# iosSimulatorArm64   -> iphonesimulator / arm64 (Apple Silicon simulators)
# iosX64              -> iphonesimulator / x86_64 (Intel Mac simulators)
sdk_for_target() {
  case "$1" in
    iosArm64) echo "iphoneos" ;;
    iosSimulatorArm64|iosX64) echo "iphonesimulator" ;;
    *) echo ""; ;;
  esac
}
arch_for_target() {
  case "$1" in
    iosArm64|iosSimulatorArm64) echo "arm64" ;;
    iosX64) echo "x86_64" ;;
    *) echo ""; ;;
  esac
}

build_target() {
  local target="$1"
  local sdk arch sysroot build_dir out_dir
  sdk="$(sdk_for_target "$target")"
  arch="$(arch_for_target "$target")"
  if [ -z "$sdk" ] || [ -z "$arch" ]; then
    echo "❌ unknown target: $target"; exit 1
  fi
  sysroot="$(xcrun --sdk "$sdk" --show-sdk-path)"
  build_dir="$BUILD_ROOT/$target"
  out_dir="$OUT_ROOT/$target"

  echo "-----------------------------------------"
  echo "▶ $target  (sdk=$sdk arch=$arch)"
  echo "  sysroot: $sysroot"
  echo "-----------------------------------------"

  rm -rf "$build_dir"
  mkdir -p "$build_dir" "$out_dir"

  # Mirror the :shared-native CMake configuration (CMakeLists.txt:37-52):
  # AOM as the LOCAL codec (encode+decode), static libs, no apps/tests/examples,
  # libyuv disabled, PIC on. Cross-compile flags target the iOS SDK/arch.
  cmake -G "$GENERATOR" \
    -S "$LIBAVIF_DIR" -B "$build_dir" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_SYSTEM_NAME=iOS \
    -DCMAKE_OSX_SYSROOT="$sysroot" \
    -DCMAKE_OSX_ARCHITECTURES="$arch" \
    -DCMAKE_OSX_DEPLOYMENT_TARGET="$DEPLOYMENT_TARGET" \
    -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
    -DBUILD_SHARED_LIBS=OFF \
    -DAVIF_CODEC_AOM=LOCAL \
    -DAVIF_CODEC_AOM_ENCODE=ON \
    -DAVIF_CODEC_AOM_DECODE=ON \
    -DAVIF_LIBYUV=OFF \
    -DAVIF_BUILD_APPS=OFF \
    -DAVIF_BUILD_TESTS=OFF \
    -DAVIF_BUILD_EXAMPLES=OFF \
    -DCONFIG_PIC=1 \
    -DENABLE_CCACHE=OFF \
    -DENABLE_DOCS=OFF \
    -DENABLE_TESTS=OFF \
    -DENABLE_TOOLS=OFF \
    -DENABLE_EXAMPLES=OFF \
    -DENABLE_NASM=OFF

  cmake --build "$build_dir" --config Release --target avif "${BUILD_FLAGS[@]}"

  # Collect the produced static libs. With BUILD_SHARED_LIBS=OFF libavif names
  # its static archive libavif_internal.a (the avifEncoder*/avifDecoder*/
  # avifImage* symbols + the bundled third_party/libyuv live here). AOM is built
  # via FetchContent into _deps/aom-build as libaom.a (NOT libaom_version.a,
  # which is a tiny version stub). We copy them out as libavif.a / libaom.a so
  # the cinterop linkerOpts `-lavif -laom` resolve.
  local avif_a aom_a
  avif_a="$(find "$build_dir" -name 'libavif_internal.a' -o -name 'libavif.a' | head -1)"
  aom_a="$(find "$build_dir" "$LIBAVIF_DIR/ext/aom" -name 'libaom.a' ! -name 'libaom_version.a' | head -1)"

  if [ -z "$avif_a" ]; then echo "❌ libavif.a not produced for $target"; exit 1; fi
  if [ -z "$aom_a" ];  then echo "❌ libaom.a not produced for $target";  exit 1; fi

  cp "$avif_a" "$out_dir/libavif.a"
  cp "$aom_a"  "$out_dir/libaom.a"

  echo "✅ $target:"
  echo "   $out_dir/libavif.a  ($(du -h "$out_dir/libavif.a" | cut -f1))"
  echo "   $out_dir/libaom.a   ($(du -h "$out_dir/libaom.a" | cut -f1))"

  # Sanity: confirm the encode entry point is present and the arch matches.
  if ! nm -gU "$out_dir/libavif.a" 2>/dev/null | grep -q 'avifEncoderWrite'; then
    echo "⚠️  warning: avifEncoderWrite not found in libavif.a for $target"
  fi
  lipo -info "$out_dir/libavif.a" 2>/dev/null || true
  echo ""
}

TARGETS=("$@")
if [ ${#TARGETS[@]} -eq 0 ]; then
  TARGETS=(iosArm64 iosSimulatorArm64 iosX64)
fi

for t in "${TARGETS[@]}"; do
  build_target "$t"
done

echo "========================================="
echo "✅ Done. Static libs in: $OUT_ROOT"
echo "========================================="
