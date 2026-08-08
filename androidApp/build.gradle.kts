import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The Android application shell for AVIF Studio. Deliberately thin: an Activity, a manifest and
// launcher resources. Everything the user sees lives in :composeApp, which is shared with iOS.
plugins {
  alias(libs.plugins.androidApplication)
  // The Compose compiler plugin must be applied here too, not just in :composeApp — MainActivity
  // passes a @Composable lambda to setContent, and without the plugin that lambda is compiled as a
  // plain Function0 and blows up at runtime with NoSuchMethodError.
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.ktfmt)
}

ktfmt { googleStyle() }

android {
  namespace = "com.alfikri.rizky.avifstudio"
  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "com.alfikri.rizky.avifstudio"
    minSdk = libs.versions.android.minSdk.get().toInt()
    targetSdk = libs.versions.android.targetSdk.get().toInt()
    versionCode = 1
    versionName = "1.0"
  }

  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

  buildTypes {
    getByName("release") {
      // No shrinking yet: the release build is not signed or published from this repo, and
      // enabling R8 without keep rules for the Kotlin/Native-adjacent code is a separate change.
      isMinifyEnabled = false
    }
  }

  buildFeatures { compose = true }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }

dependencies {
  implementation(projects.composeApp)

  // Consumed directly by MainActivity. :composeApp declares these as commonMain `implementation`,
  // so they do not transit — declaring them here keeps :composeApp's API surface unchanged.
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.core.ktx)
  implementation(libs.filekit.core)
  implementation(libs.filekit.dialogs)

  testImplementation(libs.kotlin.testJunit)
}
