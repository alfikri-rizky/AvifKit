# Publishing AvifKit as a KMM Library

This guide covers everything you need to publish AvifKit to Maven Central for others to use.

## 📋 Pre-Publishing Checklist

### 1. **Account Setup** (One-time)

#### Create Sonatype OSSRH Account
1. Go to https://issues.sonatype.org
2. Create a JIRA account
3. Create a "New Project" ticket:
   - **Project**: Community Support - Open Source Project Repository Hosting (OSSRH)
   - **Issue Type**: New Project
   - **Summary**: Request for io.github.alfikri-rizky or com.alfikri.rizky
   - **Group Id**: `io.github.alfikri-rizky` (recommended for GitHub users)
   - **Project URL**: https://github.com/alfikri-rizky/AvifKit
   - **SCM URL**: https://github.com/alfikri-rizky/AvifKit.git

4. Wait for ticket approval (usually 1-2 business days)
5. Verify ownership by creating a GitHub repo or DNS TXT record

#### Generate GPG Keys for Signing
```bash
# Generate GPG key
gpg --gen-key
# Use your real name and email
# Choose a strong passphrase

# List keys
gpg --list-keys

# Export private key (save this securely!)
gpg --export-secret-keys 4FED1E42220AD285 > gpg-secret.key

# Export public key to keyserver
gpg --keyserver keyserver.ubuntu.com --send-keys 4FED1E42220AD285
```

### 2. **Configure Credentials**

Create `~/.gradle/gradle.properties`:

```properties
# Sonatype credentials (from OSSRH JIRA account)
ossrhUsername=your-jira-username
ossrhPassword=your-jira-password

# GPG signing
signing.keyId=YOUR_LAST_8_DIGITS_OF_KEY_ID
signing.password=YOUR_GPG_PASSPHRASE
signing.secretKeyRingFile=/Users/yourname/.gnupg/secring.gpg

# Or use in-memory key (better for CI/CD)
# signing.key=<base64-encoded-gpg-key>
# signing.password=YOUR_GPG_PASSPHRASE
```

**⚠️ Never commit these credentials to Git!**

### 3. **Update Library Metadata**

#### Create `gradle.properties` in project root:

```properties
# Library Info
GROUP=io.github.alfikri-rizky
VERSION_NAME=1.0.0

# Project Info
POM_NAME=AvifKit
POM_DESCRIPTION=Kotlin Multiplatform library for AVIF image conversion
POM_INCEPTION_YEAR=2025
POM_URL=https://github.com/alfikri-rizky/AvifKit

# License
POM_LICENSE_NAME=MIT License
POM_LICENSE_URL=https://opensource.org/licenses/MIT
POM_LICENSE_DIST=repo

# Developer Info
POM_DEVELOPER_ID=alfikri-rizky
POM_DEVELOPER_NAME=Alfikri Rizky
POM_DEVELOPER_EMAIL=your.real.email@example.com

# SCM
POM_SCM_URL=https://github.com/alfikri-rizky/AvifKit
POM_SCM_CONNECTION=scm:git:git://github.com/alfikri-rizky/AvifKit.git
POM_SCM_DEV_CONNECTION=scm:git:ssh://github.com/alfikri-rizky/AvifKit.git
```

#### Update `shared/build.gradle.kts`:

Replace line 126 with actual email:
```kotlin
email.set("your.real.email@example.com")
```

### 4. **Add More iOS Targets** (Recommended)

Add x64 simulator support in `shared/build.gradle.kts`:

```kotlin
listOf(
    iosArm64(),          // Real iOS devices
    iosX64(),            // Intel Mac simulators
    iosSimulatorArm64()  // Apple Silicon simulators
).forEach { iosTarget ->
    // ... existing config
}
```

### 5. **Create Library README**

Create `shared/README.md`:

```markdown
# AvifKit

Kotlin Multiplatform library for AVIF image conversion supporting Android and iOS.

## Features
- ✅ Convert images to AVIF format
- ✅ Adaptive compression strategies (SMART/STRICT)
- ✅ Quality presets and custom parameters
- ✅ iOS and Android support

## Installation

### Android
```gradle
dependencies {
    implementation("io.github.alfikri-rizky:avifkit:1.0.0")
}
```

### iOS (CocoaPods)
```ruby
pod 'AvifKit', '~> 1.0.0'
```

### iOS (SPM)
```swift
dependencies: [
    .package(url: "https://github.com/alfikri-rizky/AvifKit", from: "1.0.0")
]
```

## Usage

### Basic Conversion
\`\`\`kotlin
val converter = AvifConverter()
val input = ImageInput.from(imageData)
val avifData = converter.encodeAvif(
    input = input,
    priority = Priority.BALANCED
)
\`\`\`

### With Custom Parameters
\`\`\`kotlin
val options = EncodingOptions(
    quality = 80,
    speed = 6,
    maxSize = 500_000, // 500KB
    compressionStrategy = CompressionStrategy.SMART
)
val avifData = converter.encodeAvif(input, options = options)
\`\`\`

## iOS Integration

For iOS, you need to add the Swift bridge file. See [Swift Integration Guide](docs/SWIFT_INTEGRATION_SETUP.md).

## License
MIT License - see [LICENSE](LICENSE) file.
```

### 6. **Create CHANGELOG.md**

```markdown
# Changelog

All notable changes to this project will be documented in this file.

## [1.0.0] - 2025-01-XX

### Added
- Initial release
- AVIF encoding for Android and iOS
- Compression strategies (SMART/STRICT)
- Quality presets (SPEED/BALANCED/QUALITY/STORAGE)
- Custom parameters support
- Adaptive compression with target file size
```

### 7. **Add LICENSE File**

Create `LICENSE` in project root:

```
MIT License

Copyright (c) 2025 Alfikri Rizky

Permission is hereby granted, free of charge, to any person obtaining a copy...
[Full MIT license text]
```

## 🚀 Publishing Steps

### Local Testing

```bash
# Build all variants
./gradlew :shared:build

# Publish to Maven Local (for testing)
./gradlew :shared:publishToMavenLocal

# Test in another project
# In that project's build.gradle.kts:
repositories {
    mavenLocal()
}
dependencies {
    implementation("io.github.alfikri-rizky:avifkit:0.1.0")
}
```

### Publish to Maven Central

```bash
# 1. Clean build
./gradlew clean

# 2. Build and sign artifacts
./gradlew :shared:build

# 3. Publish to staging repository
./gradlew :shared:publishAllPublicationsToSonatypeRepository

# 4. Go to https://s01.oss.sonatype.org
#    - Login with OSSRH credentials
#    - Click "Staging Repositories"
#    - Find your repository (io.github.alfikri-rizky-XXXX)
#    - Click "Close" button
#    - Wait for validation (~10 minutes)
#    - Click "Release" button

# 5. Wait for sync to Maven Central (~2 hours)
#    Your library will be available at:
#    https://repo1.maven.org/maven2/io/github/alfikri-rizky/avifkit/
```

### Automated Publishing with GitHub Actions

Create `.github/workflows/publish.yml`:

```yaml
name: Publish to Maven Central

on:
  release:
    types: [published]

jobs:
  publish:
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/gradle-build-action@v2

      - name: Publish to Maven Central
        env:
          OSSRH_USERNAME: ${{ secrets.OSSRH_USERNAME }}
          OSSRH_PASSWORD: ${{ secrets.OSSRH_PASSWORD }}
          SIGNING_KEY: ${{ secrets.SIGNING_KEY }}
          SIGNING_PASSWORD: ${{ secrets.SIGNING_PASSWORD }}
        run: |
          ./gradlew :shared:publishAllPublicationsToSonatypeRepository \
            --no-daemon --stacktrace
```

**Set GitHub Secrets:**
- Go to GitHub repo → Settings → Secrets and variables → Actions
- Add:
  - `OSSRH_USERNAME`
  - `OSSRH_PASSWORD`
  - `SIGNING_KEY` (base64-encoded GPG key)
  - `SIGNING_PASSWORD`

## 📦 What Gets Published

Your library will be published as multiple artifacts:

### Android
```gradle
// Main multiplatform artifact (recommended)
implementation("io.github.alfikri-rizky:avifkit:1.0.0")

// Or Android-specific
implementation("io.github.alfikri-rizky:avifkit-android:1.0.0")
```

### iOS
iOS developers will use:
- `avifkit-iosarm64` - Real devices
- `avifkit-iosx64` - Intel simulators
- `avifkit-iossimulatorarm64` - Apple Silicon simulators

Via CocoaPods or SPM (needs additional setup).

## 📚 Post-Publishing

### 1. **Tag the Release**
```bash
git tag -a v1.0.0 -m "Release v1.0.0"
git push origin v1.0.0
```

### 2. **Create GitHub Release**
- Go to GitHub → Releases → Create new release
- Choose tag `v1.0.0`
- Title: "v1.0.0 - Initial Release"
- Description: Copy from CHANGELOG.md
- Upload binaries (optional)

### 3. **Announce**
- Update GitHub repo description
- Add topics: `kotlin-multiplatform`, `kmm`, `avif`, `android`, `ios`
- Share on social media, dev.to, Medium, etc.

### 4. **Documentation Website** (Optional)
- Setup GitHub Pages
- Use Dokka for API docs:
```bash
./gradlew :shared:dokkaHtml
```

## 🔄 Version Updates

For subsequent releases:

1. Update `VERSION_NAME` in `gradle.properties`
2. Update `CHANGELOG.md`
3. Run publishing steps
4. Create new GitHub release

### Version Naming
- `1.0.0` - Initial stable release
- `1.0.1` - Bug fixes
- `1.1.0` - New features (backward compatible)
- `2.0.0` - Breaking changes
- `1.0.0-SNAPSHOT` - Development version

## ⚠️ Important Notes

1. **First-time publishing** can take 1-2 business days for OSSRH ticket approval
2. **Artifacts sync** to Maven Central in ~2 hours after release
3. **Group ID** must match your approved namespace
4. **Signing is mandatory** for Maven Central
5. **POM metadata** must be complete (name, description, URL, license, developers, SCM)
6. **iOS integration** requires users to add Swift bridge file (document this clearly!)

## 🆘 Troubleshooting

### "401 Unauthorized"
- Check OSSRH credentials in `~/.gradle/gradle.properties`
- Verify credentials at https://s01.oss.sonatype.org

### "Signature verification failed"
- Upload GPG public key: `gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID`
- Check signing.keyId matches your GPG key

### "Invalid POM"
- Ensure all required POM fields are filled
- Check for valid URLs

### "Group ID not approved"
- Wait for OSSRH JIRA ticket approval
- Verify ownership as requested

## 📖 Additional Resources

- [Maven Central Guide](https://central.sonatype.org/publish/publish-guide/)
- [Kotlin Multiplatform Publishing](https://kotlinlang.org/docs/multiplatform-publish-lib.html)
- [GPG Key Guide](https://central.sonatype.org/publish/requirements/gpg/)
