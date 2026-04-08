# Play Release Automation

This repo now has a PowerShell helper for Google Play releases:

```powershell
.\tools\play-release.ps1
```

## What it does

- defaults to this app package: `com.queukat.train`
- builds a release `.aab` with `:app:bundleRelease`
- validates the upload against Google Play by default
- publishes only when you add `-Upload`
- skips Play metadata, images, screenshots, and changelogs unless you handle those separately

## Required setup

### Play access

Required:

- `PLAY_KEY_FILE`

Optional for this script:

- `PLAY_PACKAGE_NAME`
- `PLAY_DEV_ID`

`PLAY_PACKAGE_NAME` is intentionally not trusted by default here because this machine is used for multiple apps. The script targets `com.queukat.train` unless you explicitly pass `-PackageName`.

### Signing

If the script is building the bundle itself, release signing must be configured by one of these:

1. local `keystore.properties`
2. environment variables:

```text
TRAINAPP_KEYSTORE_FILE
TRAINAPP_KEYSTORE_PASSWORD
TRAINAPP_KEY_ALIAS
TRAINAPP_KEY_PASSWORD
```

If you already have a signed `.aab`, you can skip the build step and upload that artifact directly.

## Common commands

Check Play Console access only:

```powershell
.\tools\play-release.ps1 -CheckAccessOnly
```

Build and validate against the internal track without publishing:

```powershell
.\tools\play-release.ps1
```

Build and upload to the internal track:

```powershell
.\tools\play-release.ps1 -Upload
```

Upload an already built `.aab`:

```powershell
.\tools\play-release.ps1 -SkipBuild -AabPath .\app\build\outputs\bundle\release\app-release.aab -Upload
```

Upload to production as a draft release:

```powershell
.\tools\play-release.ps1 -Track production -ReleaseStatus draft -Upload
```

Override version during build and upload:

```powershell
.\tools\play-release.ps1 -VersionCode 42 -VersionName 1.2.0 -Upload
```

## Notes

- default track is `internal`
- default behavior is validation-only
- `fastlane` must be available in `PATH`
- the script suppresses fastlane update checks to avoid the Windows permission noise seen in ad-hoc runs
