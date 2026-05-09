# Play Release Governance Rail

TrainMe publishes Android artifacts through a private release rail. The runner itself is intentionally not part of the public source tree; this document defines the control surface that any local or CI release runner must preserve.

## Control Surface

- target the TrainMe package by default: `com.queukat.train`
- build a release `.aab` with `:app:bundleRelease`, or accept an already signed `.aab`
- verify that the final `.aab` is signed before any Play upload
- validate against Google Play before publication
- publish only when upload intent is explicit
- leave Play listing metadata, images, and screenshots untouched unless those assets are being handled deliberately
- stage changelogs from `app/fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` when present

## Required Setup

### Play Access

Required:

- `PLAY_KEY_FILE`

Optional:

- `PLAY_PACKAGE_NAME`
- `PLAY_DEV_ID`

`PLAY_PACKAGE_NAME` should not be trusted blindly on shared machines. The release rail should target `com.queukat.train` unless a different package is passed explicitly.

Release environment values may be acquired from Process, User, and Machine scopes before Gradle runs. Keep environment loading, package targeting, signing checks, and Play validation in one governed path.

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

An already signed `.aab` may enter the rail directly.

The signing gate must verify the produced artifact after the build with `jarsigner`. An unsigned bundle is rejected before Play upload.

### What's New Command Surface

Every public Play release needs review-ready "What's new" text. This is part of the release contract: users should see the value of the update before implementation mechanics.

Release notes live in:

```text
app/fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt
```

Before production upload, create or update changelog files for the version code being released. Keep the text short, passenger-facing, and free of internal implementation jargon. Lead with visible control gained by the user: smaller install footprint, faster startup, stronger reminders, clearer station search, corrected localization, or a cleaner release surface.

The release rail should upload those changelogs by default. It should stage only the Play locales configured for release so non-Play metadata folders cannot contaminate the upload. Skip changelogs only for an intentional metadata-only or emergency operation where updating public release notes would mislead users.

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

## Operating Modes

The private release rail should support these modes:

- Play Console access check
- internal-track validation without publishing
- internal-track upload
- production draft upload
- production completed upload
- already-built `.aab` upload
- explicit version override during build

## Rail Notes

- default track should be `internal`
- default behavior should be validation-only
- production release should require explicit completed-upload intent
- changelogs should come from `app/fastlane/metadata/android`
- default changelog locales are `en-GB`, `en-US`, `ru-RU`, and `sr`
- "What's new" text is a release artifact, not optional polish
- fastlane must be available where the private release rail runs
- fastlane update checks should be suppressed to avoid Windows permission noise during controlled release runs
