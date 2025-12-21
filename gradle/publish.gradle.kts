/**
 * Maven Publishing Configuration for AvifKit
 * Apply this script to modules that should be published to Maven Central
 */

apply(plugin = "maven-publish")
apply(plugin = "signing")

// Read properties
val GROUP: String by project
val VERSION_NAME: String by project
val POM_NAME: String by project
val POM_DESCRIPTION: String by project
val POM_INCEPTION_YEAR: String by project
val POM_URL: String by project
val POM_LICENSE_NAME: String by project
val POM_LICENSE_URL: String by project
val POM_LICENSE_DIST: String by project
val POM_DEVELOPER_ID: String by project
val POM_DEVELOPER_NAME: String by project
val POM_DEVELOPER_EMAIL: String by project
val POM_SCM_URL: String by project
val POM_SCM_CONNECTION: String by project
val POM_SCM_DEV_CONNECTION: String by project

group = GROUP
version = VERSION_NAME

configure<PublishingExtension> {
    publications {
        withType<MavenPublication> {
            // Customize artifact ID based on variant
            artifactId = when (name) {
                "kotlinMultiplatform" -> "avifkit"
                "androidRelease" -> "avifkit-android"
                "iosArm64" -> "avifkit-ios-arm64"
                "iosSimulatorArm64" -> "avifkit-ios-simulator-arm64"
                else -> "avifkit-$name"
            }

            pom {
                name.set(POM_NAME)
                description.set(POM_DESCRIPTION)
                inceptionYear.set(POM_INCEPTION_YEAR)
                url.set(POM_URL)

                licenses {
                    license {
                        name.set(POM_LICENSE_NAME)
                        url.set(POM_LICENSE_URL)
                        distribution.set(POM_LICENSE_DIST)
                    }
                }

                developers {
                    developer {
                        id.set(POM_DEVELOPER_ID)
                        name.set(POM_DEVELOPER_NAME)
                        email.set(POM_DEVELOPER_EMAIL)
                    }
                }

                scm {
                    url.set(POM_SCM_URL)
                    connection.set(POM_SCM_CONNECTION)
                    developerConnection.set(POM_SCM_DEV_CONNECTION)
                }

                issueManagement {
                    system.set("GitHub Issues")
                    url.set("$POM_URL/issues")
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

configure<SigningExtension> {
    // Use in-memory ASCII-armored keys
    val signingKey = findProperty("signing.key")?.toString() ?: System.getenv("SIGNING_KEY")
    val signingPassword = findProperty("signing.password")?.toString() ?: System.getenv("SIGNING_PASSWORD")

    if (signingKey != null && signingPassword != null) {
        @Suppress("UnstableApiUsage")
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(the<PublishingExtension>().publications)
    }
}

// Only sign non-snapshot releases
tasks.withType<Sign>().configureEach {
    onlyIf { !version.toString().endsWith("SNAPSHOT") }
}

// Javadoc JAR generation for all platforms
val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

// Add javadoc to all publications
afterEvaluate {
    configure<PublishingExtension> {
        publications.withType<MavenPublication> {
            artifact(javadocJar)
        }
    }
}
