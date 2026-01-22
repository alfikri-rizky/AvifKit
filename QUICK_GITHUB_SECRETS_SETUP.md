# Quick GitHub Secrets Setup

## 🚀 Copy-Paste Ready Values

Go to: https://github.com/alfikri-rizky/AvifKit/settings/secrets/actions

Click **"New repository secret"** and add these 4 secrets:

---

### 1. MAVEN_CENTRAL_USERNAME

**Name:** `MAVEN_CENTRAL_USERNAME`

**Value:**
```
MEmFC5
```

---

### 2. MAVEN_CENTRAL_PASSWORD

**Name:** `MAVEN_CENTRAL_PASSWORD`

**Value:**
```
VacRS7HmpmxjPeFu0ipQen401fnbhLevV
```

---

### 3. SIGNING_IN_MEMORY_KEY

**Name:** `SIGNING_IN_MEMORY_KEY`

**Value:** Copy from your `gradle.properties` file, lines 19-102

It starts with:
```
-----BEGIN PGP PRIVATE KEY BLOCK-----
```

And ends with:
```
-----END PGP PRIVATE KEY BLOCK-----
```

**How to get it:**
```bash
# From your terminal, run this command:
awk '/signingInMemoryKey=/{flag=1; next} /signingInMemoryKeyPassword=/{flag=0} flag' gradle.properties | sed 's/\\n/\n/g'
```

**IMPORTANT:**
- Include the full key with BEGIN and END lines
- Keep all the line breaks (the command above converts `\n` to actual newlines)
- Don't modify anything

**Quick copy method:**
1. Open `gradle.properties` in editor
2. Find line 19: `signingInMemoryKey=-----BEGIN PGP PRIVATE KEY BLOCK-----\n\`
3. Select everything from `-----BEGIN` to `-----END PGP PRIVATE KEY BLOCK-----` (before `signingInMemoryKeyPassword`)
4. The value includes all the `\n\` sequences - GitHub will handle them correctly
5. Or use the awk command above to get it with proper line breaks

---

### 4. SIGNING_IN_MEMORY_KEY_PASSWORD

**Name:** `SIGNING_IN_MEMORY_KEY_PASSWORD`

**Value:**
```
Lapislazuli00@@
```

---

## ✅ Verification

After adding all 4 secrets, you should see:
- ✅ MAVEN_CENTRAL_USERNAME
- ✅ MAVEN_CENTRAL_PASSWORD
- ✅ SIGNING_IN_MEMORY_KEY
- ✅ SIGNING_IN_MEMORY_KEY_PASSWORD

---

## 🎯 Next Steps

Once secrets are configured:

### Test iOS Publishing (Automatic)
```bash
# Build XCFramework and get checksum locally first
./gradlew :shared:assembleXCFramework
cd shared/build/XCFrameworks/release
zip -r Shared.xcframework.zip Shared.xcframework
swift package compute-checksum Shared.xcframework.zip

# Update Package.swift line 47 with the checksum above
# Then commit and tag
git add Package.swift
git commit -m "Update checksum for v0.1.1"
git tag v0.1.1
git push origin main
git push origin v0.1.1
```

The `publish-ios.yml` workflow will run automatically and:
- ✅ Create GitHub release
- ✅ Upload XCFramework
- ✅ SPM package ready!

### Test Maven Central Publishing

**Option 1: Automatic (when you create GitHub release)**
1. Go to https://github.com/alfikri-rizky/AvifKit/releases
2. Click "Draft a new release"
3. Choose tag `v0.1.1`
4. Publish release
5. Workflow runs automatically

**Option 2: Manual trigger**
1. Go to https://github.com/alfikri-rizky/AvifKit/actions
2. Click "Publish to Maven Central"
3. Click "Run workflow"
4. Enter version: `0.1.1`
5. Click "Run workflow"

After workflow succeeds:
1. Go to https://central.sonatype.com/
2. Login with username: `MEmFC5` and password: `VacRS7HmpmxjPeFu0ipQen401fnbhLevV`
3. Click "Deployments"
4. Find `io.github.alfikri-rizky:avifkit`
5. Click "Publish"

---

## 📝 Important Notes

### About the GPG Key

The GPG key in your gradle.properties has `\n\` sequences. GitHub Actions will handle these correctly. You can either:

**Method 1: Copy as-is with \n\ sequences** (Easiest)
- Just copy the value from gradle.properties including all the `\n\` parts
- GitHub will automatically convert them during the workflow

**Method 2: Convert to actual line breaks**
- Use the awk command provided above
- This gives you a properly formatted multi-line key
- Easier to verify it's correct

Both methods work!

---

## 🔒 Security Note

**NEVER commit these values to Git!**

Your `gradle.properties` file contains sensitive credentials. Make sure it's in `.gitignore`:

```bash
# Check if gradle.properties is ignored
git check-ignore gradle.properties

# If not, add it to .gitignore
echo "gradle.properties" >> .gitignore
```

The secrets are only stored in GitHub's secure secrets storage and injected during workflow runs.

---

## ✅ Done!

Once all 4 secrets are added, your GitHub Actions workflows are ready to publish automatically! 🚀
