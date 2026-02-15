#!/bin/bash

# Script to clean Xcode and SPM caches to resolve package resolution issues
# Usage: ./scripts/clean-spm-cache.sh

set -e

echo "🧹 Cleaning Xcode and SPM Caches"
echo "================================="

# Check disk space before cleaning
echo ""
echo "📊 Current disk space:"
df -h / | tail -1

echo ""
echo "🗑️  Removing SPM caches..."
rm -rf ~/Library/Caches/org.swift.swiftpm
rm -rf ~/Library/org.swift.swiftpm
echo "✅ SPM caches removed"

echo ""
echo "🗑️  Removing Xcode DerivedData..."
DERIVED_DATA_SIZE=$(du -sh ~/Library/Developer/Xcode/DerivedData 2>/dev/null | cut -f1 || echo "0B")
echo "   (Current size: $DERIVED_DATA_SIZE)"
rm -rf ~/Library/Developer/Xcode/DerivedData/*
echo "✅ DerivedData cleared"

echo ""
echo "🗑️  Removing Xcode caches..."
XCODE_CACHE_SIZE=$(du -sh ~/Library/Caches/com.apple.dt.Xcode 2>/dev/null | cut -f1 || echo "0B")
echo "   (Current size: $XCODE_CACHE_SIZE)"
rm -rf ~/Library/Caches/com.apple.dt.Xcode/*
echo "✅ Xcode caches cleared"

echo ""
echo "📊 Disk space after cleaning:"
df -h / | tail -1

echo ""
echo "✅ Cache cleanup complete!"
echo ""
echo "📝 Next steps:"
echo "1. Open your Xcode project"
echo "2. File > Packages > Reset Package Caches"
echo "3. File > Packages > Update to Latest Package Versions"
echo "4. Clean build folder: Cmd+Shift+K"
echo "5. Build: Cmd+B"
