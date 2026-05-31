// Standalone Android library that builds the native libavif/AOM JNI wrapper (.so files).
//
// Why a separate module: AGP 9's Kotlin Multiplatform library plugin
// (com.android.kotlin.multiplatform.library, used by :shared) does NOT support
// externalNativeBuild / CMake / ndk. So the native build lives here in a plain
// com.android.library module, and :shared consumes it from its androidMain source set.
// It is published as a companion artifact (avifkit-native) so the .so files reach
// consumers of io.github.alfikri-rizky:avifkit transitively.
plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.mavenPublish)
}

group = "io.github.alfikri-rizky"
version = "0.2.9"

android {
    namespace = "com.alfikri.rizky.avifkit.nativelib"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    // Pin NDK r28+ so native .so files are 16 KB page-aligned by default (Android 15+ requirement).
    // r28 makes 16 KB alignment the toolchain default; older NDKs need explicit linker flags.
    ndkVersion = libs.versions.android.ndk.get()

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()

        // NDK configuration for native AVIF support.
        // The native library is built conditionally (see CMakeLists.txt):
        // - With libavif: true AVIF encoding/decoding
        // - Without libavif: JPEG fallback / placeholder mode
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

    // CMake configuration for the JNI wrapper.
    // Conditionally builds with or without libavif (see CMakeLists.txt).
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

// Maven Central Publishing — companion native artifact for :shared.
// Coordinates: io.github.alfikri-rizky:avifkit-native (group/version inherited from
// project.group / VERSION_NAME, artifactId overridden here so it doesn't collide with
// the global POM_ARTIFACT_ID=avifkit used by :shared in CI).
mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(artifactId = "avifkit-native")

    pom {
        name.set("AvifKit Native")
        description.set("Native libavif/AOM JNI library for AvifKit on Android (16 KB page-aligned)")
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
