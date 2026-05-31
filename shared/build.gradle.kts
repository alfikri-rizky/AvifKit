import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKotlinMultiplatformLibrary)
  alias(libs.plugins.mavenPublish)
  alias(libs.plugins.ktfmt)
}

group = "io.github.alfikri-rizky"

version = "0.2.10"

ktfmt { googleStyle() }

kotlin {
  // Android target via the AGP 9 Kotlin Multiplatform library plugin
  // (com.android.kotlin.multiplatform.library). This replaces androidTarget() plus the
  // top-level android {} block used before AGP 9. It produces a single (release) AAR.
  // NOTE: this plugin cannot build CMake/NDK code, so the native libavif build lives in
  // the separate :shared-native module, consumed below from androidMain.
  android {
    namespace = "com.alfikri.rizky.avifkit.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    withHostTest {}
  }

  // Create XCFramework for iOS distribution (CocoaPods, SPM, direct usage)
  val xcframeworkName = "Shared"
  val xcf = XCFramework(xcframeworkName)

  listOf(
      iosArm64(), // Real iOS devices
      iosX64(), // Intel Mac simulators
      iosSimulatorArm64(), // Apple Silicon simulators
    )
    .forEach { iosTarget ->
      iosTarget.binaries.framework {
        baseName = xcframeworkName
        // Dynamic framework so the Kotlin runtime + global singletons (e.g.
        // AvifKitIos) exist as a single dyld image at runtime, regardless of
        // how many SPM/Xcode link edges reach Shared.xcframework. With static
        // linking, multiple link edges produced duplicate AvifKitIos instances
        // and the Swift bridge's registerHandler() was invisible to consumer
        // call sites in AvifConverter — see v0.2.9 changelog.
        isStatic = false

        // iOS deployment target is set by the Kotlin/Native compiler per
        // target (iosArm64 / iosX64 / iosSimulatorArm64). For dynamic
        // frameworks we deliberately do NOT pass `-ios_version_min` here,
        // because for simulator targets the linker requires
        // `-ios_simulator_version_min` instead and rejects the device flag
        // with `ld: incompatible platforms: iOS-simulator - iOS`. Leaving
        // the version selection to Kotlin/Native produces the correct
        // platform-specific flag automatically. Our podspec and
        // Package.swift independently pin the consumer-facing minimum to
        // iOS 15.0 / macOS 12.0.

        // Add to XCFramework
        xcf.add(this)

        // The Swift AVIFNativeConverter will be linked by Xcode at app build time
        // See: iosApp/iosApp/Native/AVIFNativeConverter.swift
      }
    }

  sourceSets {
    commonMain.dependencies {
      // Coroutines for async operations
      implementation(libs.kotlinx.coroutines.core)
      // FileKit for cross-platform file handling
      api(libs.filekit.core)
      // kotlinx-io for FileKit's I/O operations
      implementation(libs.kotlinx.io.core)
    }
    commonTest.dependencies { implementation(libs.kotlin.test) }
    androidMain.dependencies {
      implementation(libs.kotlinx.coroutines.android)
      implementation(libs.androidx.exifinterface)
      // Native libavif/AOM JNI wrapper (.so). Runtime-only: the Kotlin side declares
      // `external fun`s and calls System.loadLibrary("avif-android-wrapper") — there is no
      // compile-time API from this module. Published transitively as avifkit-native so
      // consumers of io.github.alfikri-rizky:avifkit receive the .so automatically.
      implementation(projects.sharedNative)
    }
  }
}

// Maven Central Publishing Configuration (New Portal API)
// ========================================================
// Group and version set at top of file
// For CI/CD, these are set in .github/workflows/publish.yml

mavenPublishing {
  publishToMavenCentral()
  signAllPublications()

  pom {
    name.set("AvifKit")
    description.set(
      "Kotlin Multiplatform library for converting images to AVIF format on Android and iOS with libavif and AOM codec support"
    )
    inceptionYear.set("2025")
    url.set("https://github.com/alfikri-rizky/AvifKit")

    licenses {
      license {
        name.set("MIT License")
        url.set("https://opensource.org/licenses/MIT")
        distribution.set("repo")
      }
    }

    developers {
      developer {
        id.set("alfikri-rizky")
        name.set("Rizky Alfikri")
        email.set("rizkyalfikri@gmail.com")
      }
    }

    scm {
      url.set("https://github.com/alfikri-rizky/AvifKit")
      connection.set("scm:git:git://github.com/alfikri-rizky/AvifKit.git")
      developerConnection.set("scm:git:ssh://git@github.com/alfikri-rizky/AvifKit.git")
    }
  }
}
