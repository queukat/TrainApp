# Play Release Governance Rail

TrainMe publishes Android artifacts through a maintainer-controlled release rail. The conventional local runner is `tools/play-release.ps1`; the `tools/` directory is ignored and is not part of a public clone. This document defines the control surface that the local rail or any external controlled runner must preserve.

## Control Surface

- target the TrainMe package by default: `com.queukat.train`
- build a release `.aab` with `:app:bundleRelease`, or accept an already signed `.aab`
- verify that the final `.aab` is signed before any Play upload
- validate against Google Play before publication
- publish only when upload intent is explicit; validation is the default
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

Release environment values may be acquired from Process, User, and Machine scopes before Gradle runs. Keep environment loading, package targeting, signing checks, artifact identity, and Play validation in one governed path.

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

The release rail should upload those changelogs by default. It should stage only the Play locales configured for release so non-Play metadata folders cannot contaminate the upload. Select the intended `versionCode` explicitly and require one matching changelog file for every configured locale; historical changelogs must not be staged accidentally. The current local helper does not enforce this complete set: the operator must pass `-VersionCode` and manually confirm all six files, because an omitted version can stage historical notes and no discovered notes can cause changelog upload to be skipped. Skip changelogs only for an intentional metadata-only or emergency operation where updating public release notes would mislead users.

Current changelog locales:

```text
en-GB
en-US
ru-RU
sr
cs-CZ
sk
```

Example for `versionCode = 127`:

```text
app/fastlane/metadata/android/en-US/changelogs/127.txt
```

## Operating Modes

The maintainer-controlled release rail should support these modes:

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
- the local runner publishes only when `-Upload` is supplied
- in the current local runner, `-Upload -Track production` defaults to a completed production release; treat that exact combination as the production confirmation and review it before execution
- changelogs should come from `app/fastlane/metadata/android`
- default changelog locales are `en-GB`, `en-US`, `ru-RU`, `sr`, `cs-CZ`, and `sk`
- "What's new" text is a release artifact, not optional polish
- fastlane must be available where the maintainer-controlled release rail runs
- fastlane update checks should be suppressed to avoid Windows permission noise during controlled release runs

## Operator Preflight

Before validation or upload, confirm all of the following:

1. The selected AAB belongs to `com.queukat.train`.
2. Its version code and version name match the intended release.
3. `jarsigner` verifies the final artifact.
4. The chosen version code has a reviewed changelog in all six configured locales.
5. Validation targets the intended Play track and package.
6. `-Upload` is absent for dry validation and present only for an intentional publication.
7. Production `completed` status is deliberate; it is not a harmless test mode.
8. Screenshots, feature graphics, and other listing images remain untouched unless the separate listing-assets workflow is explicitly in scope.

The current local helper validates AAB signing, but operators must still verify package and version identity before using an existing or auto-selected signed bundle. Do not rely on “newest file” selection as proof that an artifact is the intended release.

See [Play listing assets](play_assets.md) for the separate screenshot and listing boundary.
