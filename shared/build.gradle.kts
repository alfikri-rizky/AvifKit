import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    androidTarget {
        publishLibraryVariants("release")  // Only publish release, not debug
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    // Create XCFramework for iOS distribution (CocoaPods, SPM, direct usage)
    val xcframeworkName = "Shared"
    val xcf = XCFramework(xcframeworkName)

    listOf(
        iosArm64(),          // Real iOS devices
        iosX64(),            // Intel Mac simulators
        iosSimulatorArm64()  // Apple Silicon simulators
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = xcframeworkName
            isStatic = true

            // Set iOS deployment target to 13.0 to avoid libarclite issues
            // libarclite was removed from Xcode 14+ (only needed for iOS < 9.0)
            // This matches our podspec requirement: spec.ios.deployment_target = "13.0"
            linkerOpts.add("-ios_version_min")
            linkerOpts.add("13.0")

            // Add to XCFramework
            xcf.add(this)

            // The Swift AVIFNativeConverter will be linked by Xcode at app build time
            // See: iosApp/iosApp/Native/AVIFNativeConverter.swift
        }
    }
    
    sourceSets {
        commonMain.dependencies {
            // Coroutines for async operations
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
            // FileKit for cross-platform file handling
            api("io.github.vinceglb:filekit-core:0.12.0")
            // kotlinx-io for FileKit's I/O operations
            implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.8.2")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
            implementation("androidx.exifinterface:exifinterface:1.3.7")
        }
    }
}

android {
    namespace = "com.alfikri.rizky.avifkit.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()

        // NDK configuration for native AVIF support
        // The native library is built conditionally:
        // - With libavif: true AVIF encoding/decoding
        // - Without libavif: JPEG fallback mode
        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_PLATFORM=android-21"
                )
            }
        }
    }

    // CMake configuration for JNI wrapper
    // Conditionally builds with or without libavif (see CMakeLists.txt)
    externalNativeBuild {
        cmake {
            path = file("src/androidMain/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

// Maven Central Publishing Configuration (New Portal API)
// ========================================================
// The plugin reads GROUP, VERSION_NAME, POM_ARTIFACT_ID from gradle.properties
// We explicitly configure the POM metadata here for Maven Central validation

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    pom {
        name.set("AvifKit")
        description.set("Kotlin Multiplatform library for converting images to AVIF format on Android and iOS")
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
