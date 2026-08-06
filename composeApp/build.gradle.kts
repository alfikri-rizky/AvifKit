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
  // Only here to bind the WebP encoder: iOS has no system WebP *writer* (verified — WebP is
  // missing from CGImageDestinationCopyTypeIdentifiers()), so the pods below supply one and this
  // plugin generates the Kotlin bindings for them. The app framework still reaches Xcode through
  // embedAndSignAppleFrameworkForXcode exactly as before; see the cocoapods block.
  // Version-less on purpose: the plugin ships inside the Kotlin Gradle plugin that is already on
  // the build classpath, and asking for it by version makes Gradle look for a marker artifact that
  // is not published separately.
  kotlin("native.cocoapods")
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

  iosArm64()
  iosX64()
  iosSimulatorArm64()

  // WebP encoding on iOS, and nothing else.
  //
  // UIKit and ImageIO can *read* WebP but not write it — `CGImageDestinationCopyTypeIdentifiers()`
  // on iOS lists heic and avif, never `org.webmproject.webp` — so the encoder has to come from
  // somewhere. These are the same two pods the Tracive app uses for exactly this;
  // SDWebImageWebPCoder pulls libwebp in transitively.
  //
  // Declaring pods here forces the whole framework integration through CocoaPods: the Kotlin
  // Gradle plugin refuses to run `embedAndSignAppleFrameworkForXcode` in a module that has pod
  // dependencies ("Incompatible 'embedAndSign' Task with CocoaPods Dependencies"), and the flag
  // that suppresses that is already deprecated. So iosApp now depends on `pod 'composeApp'`, and
  // the podspec's own script phase builds the framework. See iosApp/AGENTS.md.
  cocoapods {
    summary = "AVIF Studio shared UI"
    homepage = "https://github.com/alfikri-rizky/AvifKit"
    version = "1.0"
    // Matches Package.swift and the iosApp deployment target.
    ios.deploymentTarget = "15.0"
    // Keeps iosApp/Podfile in sync: Gradle runs `pod install` for it when the pods change.
    podfile = project.file("../iosApp/Podfile")

    framework {
      baseName = "ComposeApp"
      // Static: the iOS app links exactly one Kotlin framework, which statically contains both
      // this module and :shared (including the libavif/AOM archives embedded in :shared's
      // cinterop klib).
      isStatic = true
    }

    // SDWebImage core is declared explicitly because the encode call needs its symbols
    // (SDImageFormatWebP, SDImageCoderEncodeCompressionQuality), which do not come with the coder.
    pod("SDWebImage") {
      version = "~> 5.0"
      extraOpts += listOf("-compiler-option", "-fmodules")
    }

    pod("SDWebImageWebPCoder") {
      version = "~> 0.14"
      extraOpts += listOf("-compiler-option", "-fmodules")
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
