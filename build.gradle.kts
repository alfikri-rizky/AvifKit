plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    // Declared at root with `apply false` so :shared and :shared-native share a
    // single classloader for the plugin. Without this, each subproject loads
    // its own copy of MavenCentralBuildService and Gradle 9 refuses to assign
    // a service instance across classloader boundaries during publish.
    alias(libs.plugins.mavenPublish) apply false
}

// Applied to every module (:shared and :composeApp both run simulator tests).
// KGP silently disables simulator test tasks when its default device (e.g. "iPhone 14")
// doesn't exist in the installed Xcode — tests then never run without any failure.
// A pinned name breaks the same way when the CI runner image moves (Xcode 26.5 dropped
// "iPhone 16"), so resolve an iPhone that actually exists in the installed Xcode;
// override with -PiosSimulatorDevice=<name>.
val iosSimulatorDevice =
  providers
    .gradleProperty("iosSimulatorDevice")
    .orElse(
      providers
        .exec { commandLine("xcrun", "simctl", "list", "devices", "available") }
        .standardOutput
        .asText
        .map { list ->
          // Lines look like "    iPhone 17 Pro (<UDID>) (Shutdown)"; names may contain
          // parens ("iPhone SE (3rd generation)"), so anchor on the 36-char UDID.
          // Devices are grouped by runtime in ascending version order — take the last
          // match so the device runs the newest runtime (min deployment target is 15).
          Regex("""^\s+(iPhone .+?) \([0-9A-Fa-f-]{36}\)""", RegexOption.MULTILINE)
            .findAll(list)
            .lastOrNull()
            ?.groupValues
            ?.get(1)
            ?: error(
              "No available iPhone simulator in the installed Xcode — " +
                "pass -PiosSimulatorDevice=<name>"
            )
        }
    )

subprojects {
  tasks
    .withType(org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest::class)
    .configureEach {
      device.set(iosSimulatorDevice)
      enabled = true
    }
}


