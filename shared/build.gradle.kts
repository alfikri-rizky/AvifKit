import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    id("maven-publish")
    id("signing")
}

kotlin {
    androidTarget {
        publishLibraryVariants("release", "debug")
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),          // Real iOS devices
        iosX64(),            // Intel Mac simulators
        iosSimulatorArm64()  // Apple Silicon simulators
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true

            // The Swift AVIFNativeConverter will be linked by Xcode at app build time
            // See: iosApp/iosApp/Native/AVIFNativeConverter.swift
        }
    }
    
    sourceSets {
        commonMain.dependencies {
            // Coroutines for async operations
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
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
            version = "3.18.1"
        }
    }
}

// Maven Publishing Configuration
// ==============================

// Apply publishing script if properties are available
if (findProperty("GROUP") != null) {
    apply(from = "${rootProject.projectDir}/gradle/publish.gradle.kts")
}

// Fallback configuration if publish.gradle.kts is not used
if (findProperty("GROUP") == null) {
    group = findProperty("GROUP")?.toString() ?: "io.github.alfikri-rizky"
    version = findProperty("VERSION_NAME")?.toString() ?: "1.0.0"

    publishing {
        publications {
            withType<MavenPublication> {
                artifactId = when (name) {
                    "kotlinMultiplatform" -> "avifkit"
                    "androidRelease" -> "avifkit-android"
                    "iosArm64" -> "avifkit-ios-arm64"
                    "iosSimulatorArm64" -> "avifkit-ios-simulator-arm64"
                    else -> "avifkit-$name"
                }

                pom {
                    name.set("AvifKit")
                    description.set("Kotlin Multiplatform library for converting images to AVIF format")
                    url.set("https://github.com/alfikri-rizky/AvifKit")

                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }

                    developers {
                        developer {
                            id.set("alfikri-rizky")
                            name.set("Alfikri Rizky")
                            email.set("rizkyalfikri@gmail.com")
                        }
                    }

                    scm {
                        connection.set("scm:git:git://github.com/alfikri-rizky/AvifKit.git")
                        developerConnection.set("scm:git:ssh://github.com/alfikri-rizky/AvifKit.git")
                        url.set("https://github.com/alfikri-rizky/AvifKit")
                    }
                }
            }
        }

        repositories {
            maven {
                name = "sonatype"
                val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
                url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl

                credentials {
                    username = findProperty("ossrhUsername")?.toString() ?: System.getenv("OSSRH_USERNAME")
                    password = findProperty("ossrhPassword")?.toString() ?: System.getenv("OSSRH_PASSWORD")
                }
            }
        }
    }

    signing {
        val signingKey = findProperty("signing.key")?.toString() ?: System.getenv("SIGNING_KEY")
        val signingPassword = findProperty("signing.password")?.toString() ?: System.getenv("SIGNING_PASSWORD")

        if (signingKey != null && signingPassword != null) {
            useInMemoryPgpKeys(signingKey, signingPassword)
            sign(publishing.publications)
        }
    }

    tasks.withType<Sign>().configureEach {
        onlyIf { !version.toString().endsWith("SNAPSHOT") }
    }
}
