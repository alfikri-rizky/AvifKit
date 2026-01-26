# Release Notes: v0.1.3-SNAPSHOT

**Status:** 🔧 SNAPSHOT - Development Version (Not Published)
**Base Version:** v0.1.2
**Branch:** `v0.1.3-snapshot`
**Created:** 2026-01-26

## Overview

This is a **SNAPSHOT patch release** that fixes the SPM dependency issue found in v0.1.2. It maintains the old package name (`com.alfikri.rizky.avifkit`) and contains no breaking changes.

## What's Fixed

### SPM Dependency Resolution (Critical Fix)

**Problem:**
```
Failed to resolve dependencies: no versions of 'libavif-xcode' match the requirement 1.0.0..<2.0.0
```

**Root Cause:**
- Package.swift requested `libavif-Xcode` version `from: "1.0.0"`
- The repository only has versions in the 0.x range (latest: 0.11.1)

**Solution:**
- Updated dependency from `"1.0.0"` → `"0.11.1"` (latest stable version)
- Changed Shared.xcframework to use local path for development
- Added proper comments for release publishing

## Changes Summary

### Modified Files
1. **Package.swift**
   - libavif-Xcode dependency: `from: "1.0.0"` → `from: "0.11.1"`
   - Shared.xcframework: Remote URL → Local path (for SNAPSHOT development)
   - Updated comments for v0.1.3

2. **gradle.properties** (local only)
   - VERSION_NAME: `0.1.2` → `0.1.3-SNAPSHOT`

3. **README.md**
   - Updated all version references to `0.1.3-SNAPSHOT`

## Commit History

```
f72535b Bump version to 0.1.3-SNAPSHOT
25a8551 Fix: Update libavif-Xcode dependency to correct version
0711846 Release v0.1.2 - FileKit integration and AOM codec fix (base)
```

## Package Name

✅ **Keeps old package:** `com.alfikri.rizky.avifkit`

This SNAPSHOT maintains backward compatibility with v0.1.2 users.

## What's NOT Included

This patch release does **NOT** include:
- ❌ Package rename to `io.github.alfikririzky.avifkit` (that's in v0.2.0)
- ❌ AvifImageViewer demo component (reverted)
- ❌ Any other new features

## Status: Not Published

This version is **NOT PUBLISHED** to Maven Central or GitHub Releases.

### Why?
- User wants to fix additional issues before publishing
- SNAPSHOT version is for local development and testing only

## Next Steps

### Before Publishing v0.1.3:

1. **Fix remaining issues** (as identified by user)
2. **Test thoroughly:**
   - Android: All ABIs
   - iOS: SPM integration with 0.11.1 dependency
3. **Build XCFramework:**
   ```bash
   ./scripts/package-xcframework.sh
   ```
4. **Update Package.swift:**
   - Switch from local path to remote URL
   - Add checksum for Shared.xcframework.zip
5. **Create GitHub Release v0.1.3**
6. **Publish to Maven Central**

### Publishing Commands:
```bash
# When ready to publish
git tag v0.1.3
git push origin v0.1.3

# Create GitHub release with XCFramework
gh release create v0.1.3 \
  --title "v0.1.3 - SPM Dependency Fix" \
  --notes "Fixes libavif-Xcode dependency resolution issue" \
  ./build/release/Shared.xcframework.zip

# Publish to Maven Central
./gradlew publishAllPublicationsToMavenCentral --no-configuration-cache
```

## For Users

### Current v0.1.2 Users Experiencing SPM Error

**Temporary Workaround (until v0.1.3 is published):**
```swift
// Use branch reference instead of version
.package(url: "https://github.com/alfikri-rizky/AvifKit", branch: "v0.1.3-snapshot")
```

**Better Option:**
Wait for v0.1.3 official release (after remaining issues are fixed).

## Branch Comparison

| Version | Branch | Package Name | SPM Fix | Status |
|---------|--------|--------------|---------|--------|
| 0.1.2 | - | `com.alfikri.rizky.avifkit` | ❌ Broken | Published |
| 0.1.3-SNAPSHOT | `v0.1.3-snapshot` | `com.alfikri.rizky.avifkit` | ✅ Fixed | Local only |
| 0.2.0 | `main` | `io.github.alfikririzky.avifkit` | ✅ Fixed | Not published |

---

**Note:** This is a development snapshot. Do not use in production until officially released as v0.1.3.
