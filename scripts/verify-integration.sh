#!/bin/bash

# AvifKit - Integration Verification Script
# Checks if native AVIF integration is properly set up

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "========================================="
echo "AvifKit - Integration Verification"
echo "========================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

success() {
    echo -e "${GREEN}✅ $1${NC}"
}

warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

error() {
    echo -e "${RED}❌ $1${NC}"
}

# Function to check Android integration
check_android() {
    echo "Checking Android Integration..."
    echo "-----------------------------------"

    local android_score=0
    local android_total=5

    # Check if libavif directory exists
    if [ -d "$PROJECT_ROOT/shared/src/androidMain/cpp/libavif" ]; then
        success "libavif directory found"
        ((android_score++))
    else
        error "libavif not found"
        warning "Run: ./scripts/setup-android-libavif.sh"
    fi

    # Check CMakeLists.txt
    if [ -f "$PROJECT_ROOT/shared/src/androidMain/cpp/CMakeLists.txt" ]; then
        success "CMakeLists.txt exists"
        ((android_score++))
    else
        error "CMakeLists.txt missing"
    fi

    # Check JNI wrapper
    if [ -f "$PROJECT_ROOT/shared/src/androidMain/cpp/avif_jni_wrapper.cpp" ]; then
        if grep -q "HAVE_LIBAVIF" "$PROJECT_ROOT/shared/src/androidMain/cpp/avif_jni_wrapper.cpp"; then
            success "JNI wrapper has conditional compilation"
            ((android_score++))
        else
            warning "JNI wrapper missing HAVE_LIBAVIF"
        fi
    else
        error "JNI wrapper missing"
    fi

    # Check if NDK is enabled in gradle
    if grep -q "externalNativeBuild" "$PROJECT_ROOT/shared/build.gradle.kts"; then
        if grep -A5 "externalNativeBuild" "$PROJECT_ROOT/shared/build.gradle.kts" | grep -q "^[^/]*cmake"; then
            success "NDK configuration enabled"
            ((android_score++))
        else
            warning "NDK configuration commented out"
            warning "Uncomment externalNativeBuild section in shared/build.gradle.kts"
        fi
    fi

    # Check Kotlin implementation
    if [ -f "$PROJECT_ROOT/shared/src/androidMain/kotlin/com/alfikri/rizky/avifkit/AvifConverter.android.kt" ]; then
        if grep -q "external fun nativeEncode" "$PROJECT_ROOT/shared/src/androidMain/kotlin/com/alfikri/rizky/avifkit/AvifConverter.android.kt"; then
            success "Kotlin implementation has native methods"
            ((android_score++))
        else
            warning "Native methods not found in Kotlin"
        fi
    fi

    echo ""
    echo "Android Score: $android_score/$android_total"

    if [ $android_score -eq $android_total ]; then
        success "Android integration is COMPLETE! ✨"
        return 0
    elif [ $android_score -ge 3 ]; then
        warning "Android integration is PARTIAL"
        return 1
    else
        error "Android integration is INCOMPLETE"
        return 2
    fi
}

# Function to check iOS integration
check_ios() {
    echo ""
    echo "Checking iOS Integration..."
    echo "-----------------------------------"

    local ios_score=0
    local ios_total=5

    # Check Swift bridge
    if [ -f "$PROJECT_ROOT/shared/src/iosMain/swift/AVIFNativeConverter.swift" ]; then
        success "Swift bridge found"
        ((ios_score++))

        if grep -q "canImport(libavif)" "$PROJECT_ROOT/shared/src/iosMain/swift/AVIFNativeConverter.swift"; then
            success "Swift bridge has conditional compilation"
            ((ios_score++))
        fi
    else
        error "Swift bridge missing"
        warning "File should be at: shared/src/iosMain/swift/AVIFNativeConverter.swift"
    fi

    # Check CocoaPods configuration
    if [ -f "$PROJECT_ROOT/AvifKit.podspec" ]; then
        success "Podspec found"
        ((ios_score++))
    else
        warning "Podspec not found"
    fi

    # Check Package.swift for SPM
    if [ -f "$PROJECT_ROOT/Package.swift" ]; then
        success "Package.swift found (SPM)"
        ((ios_score++))
    else
        warning "Package.swift not found"
    fi

    # Check if pods are installed
    if [ -d "$PROJECT_ROOT/iosApp/Pods" ]; then
        success "CocoaPods dependencies installed"
        ((ios_score++))
    else
        warning "CocoaPods not installed"
        warning "Run: cd iosApp && pod install"
    fi

    echo ""
    echo "iOS Score: $ios_score/$ios_total"

    if [ $ios_score -eq $ios_total ]; then
        success "iOS integration is COMPLETE! ✨"
        return 0
    elif [ $ios_score -ge 3 ]; then
        warning "iOS integration is PARTIAL"
        return 1
    else
        error "iOS integration is INCOMPLETE"
        return 2
    fi
}

# Function to check documentation
check_docs() {
    echo ""
    echo "Checking Documentation..."
    echo "-----------------------------------"

    local doc_count=0

    local docs=(
        "INTEGRATION_SUMMARY.md"
        "NATIVE_INTEGRATION_COMPLETE.md"
        "IOS_INTEGRATION_GUIDE.md"
        "INTEGRATION_GUIDE.md"
        "AVIF_LIBRARY_README.md"
        "MAVEN_PUBLISHING_GUIDE.md"
    )

    for doc in "${docs[@]}"; do
        if [ -f "$PROJECT_ROOT/$doc" ]; then
            ((doc_count++))
        fi
    done

    echo "Documentation files: $doc_count/${#docs[@]}"

    if [ $doc_count -eq ${#docs[@]} ]; then
        success "All documentation present"
    else
        warning "Some documentation files missing"
    fi
}

# Main execution
main() {
    cd "$PROJECT_ROOT"

    local android_result=0
    local ios_result=0

    check_android || android_result=$?
    check_ios || ios_result=$?
    check_docs

    echo ""
    echo "========================================="
    echo "Overall Status"
    echo "========================================="

    if [ $android_result -eq 0 ] && [ $ios_result -eq 0 ]; then
        success "BOTH PLATFORMS READY! 🎉"
        echo ""
        echo "Next steps:"
        echo "1. Build Android: ./gradlew :shared:assembleRelease"
        echo "2. Build iOS: Open iosApp.xcworkspace in Xcode"
        echo "3. Test the library in your apps"
        echo ""
        exit 0
    elif [ $android_result -le 1 ] || [ $ios_result -le 1 ]; then
        warning "Integration is partially complete"
        echo ""
        echo "To complete setup:"
        if [ $android_result -ne 0 ]; then
            echo "- Android: Run ./scripts/setup-android-libavif.sh"
        fi
        if [ $ios_result -ne 0 ]; then
            echo "- iOS: Run ./scripts/setup-ios-avif.sh"
        fi
        echo ""
        exit 1
    else
        error "Integration needs attention"
        echo ""
        echo "Please review the setup guides:"
        echo "- NATIVE_INTEGRATION_COMPLETE.md"
        echo "- IOS_INTEGRATION_GUIDE.md"
        echo ""
        exit 2
    fi
}

# Run main function
main
