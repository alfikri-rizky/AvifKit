import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKotlinMultiplatformLibrary)
  alias(libs.plugins.mavenPublish)
  alias(libs.plugins.ktfmt)
}

group = "io.github.alfikri-rizky"

// Version comes from the catalog so :shared and :shared-native can never drift apart;
// CI overrides it with -PVERSION_NAME (see .github/workflows/publish.yml).
version = providers.gradleProperty("VERSION_NAME").getOrElse(libs.versions.avifkit.get())

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
    // Instrumented tests (androidDeviceTest source set): the codec path needs the real
    // .so, so it can only be exercised on a device/emulator via
    // :shared:connectedAndroidDeviceTest (or the CI emulator job).
    withDeviceTest {}
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
      // Direct libavif C-API binding (the iOS analog of the Android JNI path).
      // AvifConverter.ios.kt calls libavif via this cinterop instead of a Swift
      // handler. Header search dir points at the same libavif v1.2.1 source
      // vendored for Android. See shared/src/nativeInterop/cinterop/libavif.def.
      //
      // The per-target static codec libs (libavif.a + libaom.a, produced by
      // scripts/build-ios-libavif.sh) are EMBEDDED INTO the cinterop klib via
      // `-staticLibrary` ("embed static library to the result"). This is the key
      // to a self-contained Maven/Gradle artifact: the codec travels inside the
      // published klib, so a pure-Gradle KMP consumer links cleanly with no SPM
      // and no manual Xcode setup — the iOS analog of how avifkit-native ships
      // the Android .so transitively. (Putting the libs only in binaries'
      // linkerOpts, as v0.3.0 did, left them out of the klib and broke Maven
      // consumers — see docs/AVIFKIT_FEEDBACK.md.)
      val codecLibDir = project.file("src/nativeInterop/libs/ios/${iosTarget.name}")
      iosTarget.compilations.getByName("main").cinterops.create("libavif") {
        defFile(project.file("src/nativeInterop/cinterop/libavif.def"))
        // Same libavif source the Android :shared-native build uses (populated by
        // scripts/setup-android-libavif.sh).
        includeDirs(rootProject.file("shared-native/src/main/cpp/libavif/include"))
        extraOpts(
          "-staticLibrary",
          "libavif.a",
          "-staticLibrary",
          "libaom.a",
          "-libraryPath",
          codecLibDir.absolutePath,
        )
      }

      iosTarget.binaries.framework {
        baseName = xcframeworkName

        // Dynamic framework so the Kotlin runtime exists as a single dyld image
        // at runtime, regardless of how many SPM/Xcode link edges reach
        // Shared.xcframework. (Historically this also kept the AvifKitIos
        // singleton single; that registry is gone now that iOS calls libavif
        // directly via cinterop — see docs/IOS_CINTEROP_SOLUTION.md.)
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
    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
    }
    androidMain.dependencies {
      implementation(libs.kotlinx.coroutines.android)
      implementation(libs.androidx.exifinterface)
      // Native libavif/AOM JNI wrapper (.so). Runtime-only: the Kotlin side declares
      // `external fun`s and calls System.loadLibrary("avif-android-wrapper") — there is no
      // compile-time API from this module. Published transitively as avifkit-native so
      // consumers of io.github.alfikri-rizky:avifkit receive the .so automatically.
      implementation(projects.sharedNative)
    }
    getByName("androidDeviceTest").dependencies {
      implementation(libs.androidx.testExt.junit)
      implementation(libs.androidx.test.runner)
    }
  }
}

// KGP silently disables simulator test tasks when its default device (e.g. "iPhone 14")
// doesn't exist in the installed Xcode — tests then never run without any failure.
// Pin a device that ships with current Xcode; override with -PiosSimulatorDevice=<name>.
tasks
  .withType(org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest::class)
  .configureEach {
    device.set(providers.gradleProperty("iosSimulatorDevice").getOrElse("iPhone 16"))
    enabled = true
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
