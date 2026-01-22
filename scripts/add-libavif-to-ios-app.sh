#!/bin/bash

# Add libavif-Xcode SPM dependency to iOS demo app

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "========================================="
echo "Adding libavif to iOS Demo App"
echo "========================================="
echo ""

echo "📦 libavif-Xcode will be added as an SPM dependency"
echo ""
echo "To complete setup:"
echo "1. Open iosApp/iosApp.xcodeproj in Xcode"
echo "2. Select the project in the navigator"
echo "3. Go to 'Package Dependencies' tab"
echo "4. Click '+' button"
echo "5. Enter: https://github.com/SDWebImage/libavif-Xcode.git"
echo "6. Select version: 'Up to Next Major' from 1.0.0"
echo "7. Click 'Add Package'"
echo "8. Select 'libavif' library"
echo "9. Click 'Add Package' again"
echo ""
echo "OR run this command to open Xcode:"
echo ""
echo "  open iosApp/iosApp.xcodeproj"
echo ""
