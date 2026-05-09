# Play Release Governance Rail

TrainMe publishes Android artifacts through a governed distribution rail:

```powershell
.\tools\play-release.ps1
```

The rail exists to keep public delivery controlled: package targeting, signed artifact verification, Play validation, localized changelog staging, and explicit publication intent all pass through one operational surface.

## Control Surface

- targets the TrainMe package by default: `com.queukat.train`
- builds a release `.aab` with `:app:bundleRelease`
- verifies that the final `.aab` is signed before any Play upload
- validates against Google Play by default
- publishes only when `-Upload` is present
- leaves Play listing metadata, images, and screenshots untouched unless those assets are handled separately
- stages changelogs from `app/fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` when present

## Required Setup

### Play Access

Required:

- `PLAY_KEY_FILE`

Optional:

- `PLAY_PACKAGE_NAME`
- `PLAY_DEV_ID`

`PLAY_PACKAGE_NAME` is not trusted by default because this machine handles more than one Android product. The rail targets `com.queukat.train` unless `-PackageName` is passed explicitly.

Release environment values are acquired from Process, User, and Machine scopes before Gradle runs. Use the rail directly so environment loading, package targeting, signing checks, and Play validation stay in one governed path.

### Signing Quality Gate

When the rail builds the bundle, release signing must be configured by one of these paths:

1. local `keystore.properties`
2. environment variables:

```text
TRAINAPP_KEYSTORE_FILE
TRAINAPP_KEYSTORE_PASSWORD
TRAINAPP_KEY_ALIAS
TRAINAPP_KEY_PASSWORD
```

An already signed `.aab` can enter the rail with `-SkipBuild` and `-AabPath`.

The signing gate verifies the produced artifact after the build with `jarsigner`. An unsigned bundle is rejected before Play upload.

### What's New Command Surface

Every public Play release needs review-ready "What's new" text. This is part of the release contract: users should see the value of the update before implementation mechanics.

Release notes live in:

```text
app/fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt
```

Before production upload, create or update changelog files for the version code being released. Keep the text short, passenger-facing, and free of internal implementation jargon. Lead with visible control gained by the user: smaller install footprint, faster startup, stronger reminders, clearer station search, corrected localization, or a cleaner release surface.

The rail uploads those changelogs by default. It stages only the Play locales configured by `-ChangelogLocales` so non-Play metadata folders cannot contaminate the upload. Pass `-SkipChangelogs` only for an intentional metadata-only or emergency operation where updating public release notes would mislead users.

Current changelog locales:

```text
en-GB
en-US
ru-RU
sr
```

Example for `versionCode = 124`:

```text
app/fastlane/metadata/android/en-US/changelogs/124.txt
```

## Command Center

Check Play Console access:

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

Build, verify signature, and upload to production:

```powershell
.\tools\play-release.ps1 -Track production -ReleaseStatus completed -Upload
```

Override version during build and upload:

```powershell
.\tools\play-release.ps1 -VersionCode 42 -VersionName 1.2.0 -Upload
```

## Rail Notes

- default track is `internal`
- default behavior is validation-only
- production release should use `.\tools\play-release.ps1 -Track production -ReleaseStatus completed -Upload`
- changelogs are uploaded by default from `app/fastlane/metadata/android`
- default changelog locales are `en-GB`, `en-US`, `ru-RU`, and `sr`
- "What's new" text is a release artifact, not optional polish
- `fastlane` must be available in `PATH`
- fastlane update checks are suppressed to avoid Windows permission noise during controlled release runs
