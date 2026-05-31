import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Android-only demo app for AvifKit.
//
// AGP 9 no longer allows the Kotlin Multiplatform plugin together with com.android.application
// in the same module. Since this demo only ever targeted Android (no iOS/desktop, the iOS demo
// lives in iosApp/), it is a plain com.android.application module here rather than a KMP module.
// Compose Multiplatform + the Compose compiler plugin run on top of AGP 9's built-in Kotlin.
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.alfikri.rizky.avifkit"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.alfikri.rizky.avifkit"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    // AvifKit library (KMP) — local project with the 16 KB native fix
    implementation(projects.shared)

    // Compose Multiplatform (Android-backed on this target)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.ui)
    implementation(compose.components.uiToolingPreview)
    implementation(compose.preview)
    debugImplementation(compose.uiTooling)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor)
    implementation(libs.navigation.compose)

    testImplementation(libs.kotlin.testJunit)
}
