# Play Listing Assets

Play listing text, screenshots, and feature graphics are a separate operational surface from AAB release. Do not combine asset mutation with a normal application release unless listing assets are explicitly in scope.

## Tracked sources

- Listing text: `app/fastlane/metadata/android/<locale>/`
- Changelogs: `app/fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt`
- Screenshots: `app/fastlane/metadata/android/<locale>/images/phoneScreenshots/`
- Feature graphics: `app/fastlane/metadata/android/<locale>/images/featureGraphic.png`
- Feature-graphic generator: `scripts/generate-feature-graphic.ps1`
- Screenshot instrumentation: `app/src/androidTest/java/com/queukat/train/screenshots/StoreScreenshotTest.kt`

Maintainer-only upload/capture helpers may exist under the ignored `tools/` directory. They are not guaranteed to be present in a public clone and must not become the only documentation of a release rule.

## Locale boundaries

Play metadata locale names are not the same as Android resource qualifiers or station-language values. Current changelog locales are:

```text
en-GB
en-US
ru-RU
sr
cs-CZ
sk
```

Before publishing, verify the actual Play configuration rather than inferring it from every directory under `metadata/android`.

## Screenshot constraints

The screenshot test uses the live timetable API and captures home, route results, expanded route, and settings surfaces. Its output can vary with service availability, timetable date, emulator status bars, locale, and filename conventions.

For each screenshot refresh:

1. Pin the intended app version, device profile, Android version, locale, and timetable date.
2. Confirm the live route prerequisite is available; a skipped test is not a valid capture.
3. Review station names, times, permission banners, and status-bar content for accidental or stale data.
4. Confirm emitted filenames match the tracked listing assets and README references.
5. Compare every affected locale at the same dimensions.
6. Commit only approved final assets, not runtime XML, previews, or temporary captures.

## Safe publication boundary

- Default to validation/review before any listing upload.
- Do not assume the AAB rail's validation default applies to every asset helper: local force-upload and image-delete helpers have no dry-run/validate-only mode and can commit Play changes immediately.
- Do not delete listing images as part of routine release cleanup.
- Image deletion and Play commit are destructive external actions and require an explicit listing-assets intent.
- Keep changelog-only AAB releases from touching screenshots and feature graphics.
- Keep public copy aligned with `docs/presentation_style.md` and the independence notice.
