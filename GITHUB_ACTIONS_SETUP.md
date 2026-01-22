# GitHub Actions Setup Guide

## ✅ What Was Fixed

### Fixed `publish.yml` (Maven Central)
- ❌ **OLD**: Used deprecated OSSRH API (`publishAllPublicationsToSonatypeRepository`)
- ✅ **NEW**: Uses Maven Central Portal API (`publishToMavenCentral`)
- ❌ **OLD**: Required old OSSRH credentials
- ✅ **NEW**: Uses new Maven Central Portal credentials
- ✅ Updated to use vanniktech plugin's native secret handling
- ✅ Simplified workflow - secrets are passed via gradle.properties
- ✅ Added correct artifact name in summary (`avifkit`)

### Fixed `publish-ios.yml` (iOS/SPM)
- ❌ **OLD**: Tag pattern `[0-9]+.[0-9]+.[0-9]+` (matched 0.1.1 but not v0.1.1)
- ✅ **NEW**: Tag pattern `v[0-9]+.[0-9]+.[0-9]+` (matches v0.1.1)
- ❌ **OLD**: Used `assembleSharedXCFramework` (incorrect task)
- ✅ **NEW**: Uses `assembleXCFramework` (correct task)
- ❌ **OLD**: Complex sed commands that might fail
- ✅ **NEW**: Simple placeholder replacement
- ✅ Updated release notes with correct artifact name (`avifkit`)
- ✅ Added KMM installation instructions
- ✅ Updated documentation links

---

## 🔑 Required GitHub Secrets

You need to configure these secrets in your GitHub repository before the workflows will work.

### Navigate to GitHub Secrets

1. Go to your repository: https://github.com/alfikri-rizky/AvifKit
2. Click **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret** for each secret below

---

### For Maven Central Publishing (`publish.yml`)

#### 1. `MAVEN_CENTRAL_USERNAME`
**Value:** Your Maven Central Portal username (from https://central.sonatype.com/)

**How to get it:**
1. Go to https://central.sonatype.com/
2. Click your profile → "View Account"
3. Click "Generate User Token"
4. Copy the username

**Example:** `MEmFC5`

---

#### 2. `MAVEN_CENTRAL_PASSWORD`
**Value:** Your Maven Central Portal password/token

**How to get it:**
From the same token generation page (step 1 above), copy the password/token.

**Example:** `VacRS7HmpmxjPeFu0ipQen401fnbhLevV`

---

#### 3. `SIGNING_IN_MEMORY_KEY`
**Value:** Your GPG private key in ASCII-armored format

**How to get it:**
```bash
# Export your GPG key (the one you used for signing)
gpg --armor --export-secret-keys YOUR_KEY_ID

# Or from gradle.properties
grep "signingInMemoryKey=" gradle.properties | cut -d'=' -f2-
```

**Format:** Should start with `-----BEGIN PGP PRIVATE KEY BLOCK-----`

**IMPORTANT:**
- Copy the ENTIRE key including the header and footer
- Keep all `\n` sequences (they represent newlines)
- Don't remove or modify any part of the key

**Example:**
```
-----BEGIN PGP PRIVATE KEY BLOCK-----

lQWGBGlTSBgBDADHbtHdiDMs3hZnA4sFHEM90jgE9oay5fh/hRWJ9OfQzFwGkHm/
... (many more lines) ...
=I+mW
-----END PGP PRIVATE KEY BLOCK-----
```

---

#### 4. `SIGNING_IN_MEMORY_KEY_PASSWORD`
**Value:** The password for your GPG key

**How to get it:**
```bash
# From gradle.properties
grep "signingInMemoryKeyPassword=" gradle.properties | cut -d'=' -f2-
```

**Example:** `Lapislazuli00@@`

---

### For iOS Publishing (Optional - `publish-ios.yml`)

The iOS workflow works automatically and doesn't require secrets!

However, if you want to enable **automatic CocoaPods publishing**, you need:

#### 5. `COCOAPODS_TRUNK_TOKEN` (Optional)
**Value:** Your CocoaPods trunk token

**How to get it:**
```bash
# First, register with CocoaPods (if not already)
pod trunk register rizkyalfikri@gmail.com 'Rizky Alfikri'
# Check your email and confirm

# Get your token
pod trunk me
# Look for "Token: xxxxx"
```

Then enable it by setting a repository variable:

1. Go to **Settings** → **Secrets and variables** → **Actions** → **Variables** tab
2. Click **New repository variable**
3. Name: `ENABLE_COCOAPODS_PUBLISH`
4. Value: `true`

**Note:** CocoaPods publishing is optional. You can publish manually with:
```bash
pod trunk push AvifKit.podspec --allow-warnings
```

---

## 🚀 How the Workflows Work

### Maven Central Workflow (`publish.yml`)

**Triggered by:**
1. Manual dispatch (Actions tab → "Publish to Maven Central" → Run workflow)
2. Automatically when a GitHub release is published

**What it does:**
1. Builds the shared module
2. Runs tests
3. Publishes to Maven Central **staging repository**
4. You must manually click "Publish" in Central Portal

**After workflow succeeds:**
1. Go to https://central.sonatype.com/
2. Login with your credentials
3. Click "Deployments"
4. Find your deployment
5. Click "Publish"

---

### iOS/SPM Workflow (`publish-ios.yml`)

**Triggered by:**
Pushing a tag with format `v0.1.1`, `v1.2.3`, etc.

**What it does:**
1. Builds XCFramework
2. Calculates checksum
3. Updates Package.swift with the checksum
4. Creates GitHub release with XCFramework attached
5. Automatically makes SPM package available!
6. (Optional) Publishes to CocoaPods if enabled

**After workflow succeeds:**
- SPM package is immediately available
- Users can add it in Xcode
- No manual steps required!

---

## 📋 Complete Publishing Workflow

Here's how to publish v0.1.1:

### Step 1: Update Package.swift locally (before tagging)

```bash
# Build XCFramework
./gradlew :shared:assembleXCFramework

# Calculate checksum
cd shared/build/XCFrameworks/release
zip -r Shared.xcframework.zip Shared.xcframework
swift package compute-checksum Shared.xcframework.zip
cd ../../../..

# Update Package.swift line 47 with the real checksum
# (replace CHECKSUM_PLACEHOLDER_UPDATE_AFTER_BUILD)
```

### Step 2: Commit and Tag

```bash
git add Package.swift
git commit -m "Release v0.1.1 - Fix artifact naming..."
git tag v0.1.1
git push origin main
git push origin v0.1.1
```

### Step 3: iOS Workflow Runs Automatically ✅

The `publish-ios.yml` workflow will:
- Build XCFramework
- Create GitHub release
- Upload XCFramework zip
- Update Package.swift (if needed)

### Step 4: Trigger Maven Central Publishing

**Option A: Via Release (Automatic)**
1. Go to https://github.com/alfikri-rizky/AvifKit/releases
2. Click "Draft a new release"
3. Choose tag: `v0.1.1`
4. Fill in release notes (can copy from the auto-generated one)
5. Click "Publish release"
6. The `publish.yml` workflow runs automatically

**Option B: Manual Trigger**
1. Go to https://github.com/alfikri-rizky/AvifKit/actions
2. Click "Publish to Maven Central"
3. Click "Run workflow"
4. Enter version: `0.1.1`
5. Click "Run workflow"

### Step 5: Publish on Maven Central Portal

After the workflow succeeds:
1. Go to https://central.sonatype.com/
2. Login
3. Click "Deployments"
4. Find your deployment (`io.github.alfikri-rizky:avifkit`)
5. Click "Publish"

**Done!** Both platforms published! 🎉

---

## ✅ Verification Checklist

After publishing, verify:

### GitHub Secrets Configured
- [ ] `MAVEN_CENTRAL_USERNAME` ✅
- [ ] `MAVEN_CENTRAL_PASSWORD` ✅
- [ ] `SIGNING_IN_MEMORY_KEY` ✅
- [ ] `SIGNING_IN_MEMORY_KEY_PASSWORD` ✅
- [ ] `COCOAPODS_TRUNK_TOKEN` (optional)
- [ ] `ENABLE_COCOAPODS_PUBLISH` variable (optional)

### Package.swift
- [ ] Line 47 has real checksum (not placeholder)
- [ ] URL points to correct version (v0.1.1)
- [ ] Committed and pushed

### GitHub
- [ ] Tag `v0.1.1` exists
- [ ] Release exists with XCFramework attached
- [ ] Workflows succeeded (check Actions tab)

### Maven Central
- [ ] Deployment appears in Central Portal
- [ ] Clicked "Publish" button
- [ ] After 15 min: `https://central.sonatype.com/artifact/io.github.alfikri-rizky/avifkit/0.1.1` loads

### SPM
- [ ] Can add package in Xcode
- [ ] libavif dependency resolves automatically

---

## 🐛 Troubleshooting

### Workflow fails: "Secret not found"
**Solution:** Make sure you added the secret with the EXACT name (case-sensitive)

### Maven publish fails: "401 Unauthorized"
**Solution:** Regenerate Maven Central token at https://central.sonatype.com/

### iOS workflow fails: "assembleXCFramework not found"
**Solution:** Fixed in the updated workflow - uses correct task name

### SPM can't resolve package
**Solution:**
1. Check Package.swift has real checksum (not placeholder)
2. Ensure GitHub release exists with XCFramework zip
3. Try adding with full URL: `https://github.com/alfikri-rizky/AvifKit.git`

### GPG signing fails
**Solution:**
1. Verify the `SIGNING_IN_MEMORY_KEY` is complete (includes BEGIN/END lines)
2. Check the password is correct
3. Make sure you copied the key with all the `\n` sequences

---

## 📞 Need Help?

If workflows fail:
1. Check the Actions tab for detailed logs
2. Look for the specific error message
3. Verify all secrets are configured correctly
4. Check the secret values match your local `gradle.properties`

Good luck with your first automated release! 🚀
