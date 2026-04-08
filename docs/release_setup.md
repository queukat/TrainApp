# Release Setup

## Purpose

This project no longer reads release signing secrets from tracked files.

Safe release paths now are:
- debug build for local development
- unsigned release build when no signing config is present
- signed release build only from local non-tracked config or CI secrets

## Debug Build

Use:

```powershell
./gradlew.bat :app:assembleDebug
```

Run the app on a device or emulator with:

```powershell
./gradlew.bat :app:installDebug
```

## Unsigned Release Build

If no signing secrets are configured, release assembly still works and produces an unsigned artifact:

```powershell
./gradlew.bat :app:assembleRelease
```

Expected behavior:
- build succeeds
- Gradle prints that release signing config is not provided
- artifact is suitable for local verification, not for trusted store publishing

## Local Signing Setup

Do not put signing secrets into tracked files.

Use one of these options:

1. `keystore.properties` in repo root, based on `keystore.properties.example`
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

Example local setup:

```powershell
Copy-Item keystore.properties.example keystore.properties
```

Then fill in real local values and keep `keystore.properties` untracked.

## CI / Secret Hygiene

CI should inject signing values through secret-backed environment variables.

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
