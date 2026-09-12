# Releasing AvifKit

Anyone with **write** access to this repository can cut a complete release. Nothing in the
procedure requires the repository owner or a Sonatype login.

## Cut a release

1. Write the highlights for the version into `.github/release-notes/<version>.md`
   (e.g. `.github/release-notes/0.3.3.md`) and merge that to `main`.
2. Run the **Publish iOS** workflow (Actions → Publish iOS → Run workflow) with:
   - **version** — `0.3.3`, no `v` prefix.
   - **publish_to_maven_central** — leave checked.
3. Wait. One dispatch does everything:

   | Stage | Produces |
   | --- | --- |
   | `publish-ios` | XCFramework, the `Package.swift` checksum commit, tag `v0.3.3`, the GitHub release |
   | `publish-maven-central` | `io.github.alfikri-rizky:avifkit` and `:avifkit-native` on Maven Central |

Artifacts take roughly 15–30 minutes after the workflow goes green before Gradle can
resolve them.

### Publishing to Maven Central on its own

Use the **Publish to Maven Central** workflow directly when the GitHub release already
exists — a retry after a failed publish, or catching up a version that shipped to SPM only
(`0.2.4`, `0.2.6` and `0.3.0` are in that state). Give it the version; it builds the
matching `v<version>` tag when one exists, and warns if it has to fall back to a branch.

## Things the workflow will refuse to do

- **Publish a version that is already on Maven Central.** Central is immutable — a bad
  artifact can only be superseded, never replaced or deleted. A malformed version string
  once slipped through and `v0.3.0` (with the `v`) is a permanent entry in the repository
  as a result.
- **Publish a version that is not `x.y.z`.**
- **Run without publishing credentials.** It checks all four secrets up front rather than
  failing inside Gradle forty minutes later.

## Repository secrets

These are configured already. They are listed here for rotation, and because they are the
only part of releasing that needs repository **admin** rights.

| Secret | What it is | Where it comes from |
| --- | --- | --- |
| `MAVEN_CENTRAL_USERNAME` | Central Portal user token, username half | [central.sonatype.com](https://central.sonatype.com) → your account → Generate User Token |
| `MAVEN_CENTRAL_PASSWORD` | Central Portal user token, password half | same token |
| `SIGNING_IN_MEMORY_KEY` | Base64 of the GPG secret key that signs the artifacts | `gpg --export-secret-keys <KEY_ID> \| base64 \| tr -d '\n'` — must be one line, no wrapping |
| `SIGNING_IN_MEMORY_KEY_PASSWORD` | Passphrase for that GPG key | — |

The token is not a Sonatype account password, and the GPG public key must be on a public
keyserver or Central rejects the deployment.

Optional, for CocoaPods:

| Name | Kind | Effect |
| --- | --- | --- |
| `COCOAPODS_TRUNK_TOKEN` | secret | Credentials for `pod trunk push` |
| `ENABLE_COCOAPODS_PUBLISH` | variable | Set to `true` to run the CocoaPods step at all |

## Why the deployment publishes itself now

The publish workflow sets `mavenCentralAutomaticPublishing=true`. Without it the
gradle-maven-publish-plugin uploads the deployment as `USER_MANAGED`, and it waits in
[the Central Portal](https://central.sonatype.com/publishing/deployments) until whoever
owns the Sonatype account presses **Publish** — the one step in the pipeline a maintainer
has no way to perform. `AUTOMATIC` still runs Central's validation, so a deployment Central
rejects fails the job rather than shipping.

Both modules are published in a single Gradle invocation on purpose. The plugin batches
every module of one build into a single Central deployment, so `avifkit` and
`avifkit-native` are validated and released together or not at all.
