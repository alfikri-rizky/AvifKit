import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// AVIF Studio — the shared Compose Multiplatform UI for the Android and iOS apps.
//
// This is a KMP *library* module, not an application: AGP 9 refuses to load the Kotlin
// Multiplatform plugin alongside com.android.application in the same module, so the Android APK
// lives in :androidApp and the iOS app links the ComposeApp framework produced here. The Android
// side uses AGP 9's KMP library plugin (com.android.kotlin.multiplatform.library), the same one
// :shared uses.
plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKotlinMultiplatformLibrary)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.ktfmt)
}

ktfmt { googleStyle() }

kotlin {
  // PlatformActions and ImageCodec are expect/actual *classes*, which the compiler still treats as
  // a beta feature and warns about on every build without this.
  compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }

  android {
    // Must differ from :androidApp's namespace — AGP refuses to merge two manifests that claim
    // the same one. Only affects the generated R/BuildConfig package, not the Kotlin packages.
    namespace = "com.alfikri.rizky.avifstudio.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()
    // Compose Resources ships its strings through the Android resource/asset pipeline.
    androidResources { enable = true }
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    // Runs commonMain's pure-logic tests on the JVM via :composeApp:testAndroidHostTest.
    withHostTest {}
  }

  listOf(iosArm64(), iosX64(), iosSimulatorArm64()).forEach { iosTarget ->
    iosTarget.binaries.framework {
      baseName = "ComposeApp"
      // Static: the iOS app links exactly one Kotlin framework, which statically contains both
      // this module and :shared (including the libavif/AOM archives embedded in :shared's
      // cinterop klib). Nothing has to be embedded or code-signed separately.
      isStatic = true
    }
  }

  sourceSets {
    commonMain.dependencies {
      implementation(projects.shared)

      implementation(compose.runtime)
      implementation(compose.foundation)
      implementation(compose.material3)
      implementation(compose.ui)
      implementation(compose.components.uiToolingPreview)
      implementation(compose.components.resources)
      // BackHandler ships separately from compose.ui and is what makes the Android back gesture
      // leave Settings instead of leaving the app.
      implementation(libs.compose.backhandler)
      implementation(libs.compose.material.icons.core)

      implementation(libs.androidx.lifecycle.viewmodel)
      implementation(libs.androidx.lifecycle.viewmodelCompose)
      implementation(libs.androidx.lifecycle.runtimeCompose)
      implementation(libs.kotlinx.coroutines.core)

      // Cross-platform pickers and file access. :shared already exposes filekit-core through its
      // api() dependency; this adds the dialog/Compose layer on top.
      implementation(libs.filekit.dialogs.compose)

      // Settings persistence. The multiplatform Preferences DataStore keeps one implementation
      // for both platforms; only the file location differs.
      implementation(libs.datastore.preferences.core)
    }

    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
    }

    androidMain.dependencies {
      implementation(libs.androidx.activity.compose)
      implementation(libs.kotlinx.coroutines.android)
      // EXIF orientation for the JPEG/PNG path. AvifKit handles orientation itself on the AVIF
      // path, but re-encoding a photo through BitmapFactory here would otherwise rotate it.
      implementation(libs.androidx.exifinterface)
      implementation(libs.androidx.core.ktx)
      // Writing a batch into the folder the user picked, through a SAF tree URI.
      implementation(libs.androidx.documentfile)
    }
  }
}

compose.resources {
  // Internal: the generated Res class is an implementation detail of the UI module, not something
  // :androidApp or the iOS app should reach into.
  publicResClass = false
  packageOfResClass = "com.alfikri.rizky.avifstudio.resources"
  generateResClass = auto
}

// Android reports Indonesian with the legacy ISO 639-1 code "in", and Compose Resources matches
// its values-* qualifier against exactly what the platform reports — so a values-id folder alone
// never matches on Android. Mirror it into values-in before the resources are converted rather
// than maintaining two copies of the same translation by hand.
val generateLegacyLocales by tasks.registering {
  val resourcesDir = layout.projectDirectory.dir("src/commonMain/composeResources")
  inputs.dir(resourcesDir.dir("values-id"))
  outputs.dir(resourcesDir.dir("values-in"))
  doLast {
    val modern = resourcesDir.dir("values-id").asFile
    val legacy = resourcesDir.dir("values-in").asFile
    if (!modern.isDirectory) return@doLast
    legacy.mkdirs()
    modern.listFiles()?.forEach { source ->
      source.copyTo(File(legacy, source.name), overwrite = true)
    }
  }
}

// Every Compose Resources task reads the whole composeResources directory, so they all have to
// wait for the mirror — Gradle fails the build on an undeclared dependency, not just a stale copy.
tasks
  .matching { task ->
    task.name.contains("ValueResources", ignoreCase = true) ||
      task.name.contains("ComposeResources", ignoreCase = true) ||
      task.name.contains("ResourceAccessors", ignoreCase = true)
  }
  .configureEach { dependsOn(generateLegacyLocales) }
