# Release Governance Setup

## Purpose

TrainMe keeps release signing secrets out of tracked files and routes public delivery through explicit quality gates.

Supported release paths:

- debug build for local development
- unsigned release build for local verification when signing material is absent
- signed release build from local non-tracked config or an external controlled runner
- Play delivery through the governed release rail

For production Play delivery, use the maintainer-controlled local rail or another controlled runner that builds the bundle, verifies that the final `.aab` is signed, and only then uploads to Google Play. The conventional local runner path is `tools/play-release.ps1`; `tools/` is ignored and is not present in a public clone. Keep the signing and validation gates in any replacement runner.

GitHub Releases and tracked GitHub Actions workflows are not supported Android distribution paths for TrainMe.

Before production upload, fill the Play "What's new" changelog for the release version code under `app/fastlane/metadata/android/<locale>/changelogs/`. The changelog is the public value surface for the update.

## Debug Build

Use:

```powershell
.\gradlew.bat :app:assembleDebug --console=plain
```

Run the app on a device or emulator with:

```powershell
.\gradlew.bat :app:installDebug --console=plain
```

## Unsigned Release Build

If no signing secrets are configured, release assembly still works and produces an unsigned artifact:

```powershell
.\gradlew.bat :app:assembleRelease --console=plain
```

Expected behavior:

- build succeeds
- Gradle prints that release signing config is not provided
- artifact is suitable for local verification, not trusted store publishing

## Local Signing Governance

Do not put signing secrets into tracked files.

Use one of these paths:

1. `keystore.properties` in the repo root, based on `keystore.properties.example`
2. environment variables

Supported keys:

```text
KEYSTORE_FILE
KEYSTORE_PASSWORD
KEY_ALIAS
KEY_PASSWORD
```

Environment variable form:

```text
TRAINAPP_KEYSTORE_FILE
TRAINAPP_KEYSTORE_PASSWORD
TRAINAPP_KEY_ALIAS
TRAINAPP_KEY_PASSWORD
```

The release rail can read those values from Process, User, or Machine environment scopes and imports them into the current process before invoking Gradle.

Example local setup:

```powershell
Copy-Item keystore.properties.example keystore.properties
```

Then fill in real local values and keep `keystore.properties` untracked.

## Runner Secret Hygiene

Any external controlled runner should inject signing values through secret-backed environment variables. The local rail should use ignored configuration or environment scopes.

Do not restore any of the old tracked patterns:

- `gradle.properties` with signing secrets
- tracked keystore files in the repo
- base64 keystore blobs committed into the tree

## Residual Risk

The old release keystore was previously exposed in the repository history/worktree.

That means:

- the old key should be treated as compromised
- it must be rotated before any trusted release
- public history cleanup is still recommended if the old material remains recoverable
