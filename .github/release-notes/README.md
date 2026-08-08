# Release notes

One file per version, named `<version>.md` without the `v` prefix — e.g. `0.3.3.md`.

The `publish-ios` workflow reads the file matching the version being released and uses it as the
body of the GitHub release, then appends the install snippet, the XCFramework SHA-256, and
GitHub's auto-generated commit list. If the file is missing the release still publishes, but with
no highlights and a workflow warning.

Write what a developer needs in order to decide whether to upgrade:

- Lead with what the release *is* — the bug it fixes, the thing it changes.
- Say what breaks and what the caller has to do about it.
- Skip the feature list. It's the same every release and it's already in the README.
