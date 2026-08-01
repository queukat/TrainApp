# Developer Handoff Checklist

Use the smallest verification set that matches the change. Record what actually ran; do not turn skipped or unavailable checks into implied passes.

## Before editing

- [ ] Run `git status --short` and preserve unrelated work.
- [ ] Identify the owning layer and the public/user-visible contract.
- [ ] Check whether strings, locales, persistence, permissions, or release notes are affected.
- [ ] For more than three related fixes, update `docs/architecture-findings.md`.

## Implementation

- [ ] Keep parsing at the external input boundary.
- [ ] Use typed domain values; represent money as integer minor units with currency/scale.
- [ ] Preserve user data and backward compatibility; do not add destructive migrations without an explicit decision.
- [ ] Prefer a code fix over a warning suppression or compatibility adapter.
- [ ] Keep public wording consistent with `docs/presentation_style.md`.

## Verification selection

- [ ] Unit tests for changed models, helpers, mapping, and time behavior.
- [ ] Debug compile for source/resource changes.
- [ ] Detekt/ktlint for Kotlin changes.
- [ ] Android lint for manifest/resource/API-level changes.
- [ ] Device/emulator flow for Compose UI, preferences, permissions, and reminders.
- [ ] Migration/DAO verification for Room schema changes.
- [ ] Screenshot refresh for public UI presentation changes.

Canonical commands are listed in [Testing and quality](testing.md). The repository does not currently define a `verifyTranslations` Gradle task.

## Strings and public copy

- [ ] Review every affected app locale and preserve format placeholders.
- [ ] Keep app locales, station-name languages, and Play metadata locales distinct.
- [ ] Use “notification reminder”, not “push”, for the current on-device AlarmManager path.
- [ ] Keep the ZPCG independence statement explicit.
- [ ] Do not claim ticketing, live tracking, guaranteed accuracy, partnerships, uptime, or unsupported scale.

## Release-facing changes

- [ ] Confirm `versionCode`/`versionName` and changelog filenames match.
- [ ] Update the configured Play changelog locales when passenger-visible behavior changes.
- [ ] Keep listing images/screenshots separate from AAB release unless explicitly in scope.
- [ ] Validate signing before Play access; publishing must require explicit upload intent.
- [ ] Never commit signing material, Play credentials, generated APK/AAB files, or maintainer-only tooling.

## Handoff

- [ ] Run `git diff --check`.
- [ ] Review `git diff --stat` and the final changed-file list.
- [ ] Confirm documentation links resolve.
- [ ] Record verification as a ledger:

```text
PASS | <command or manual check> | <evidence>
SKIP | <check> | <explicit reason>
FAIL | <check> | <failure and next action>
```

- [ ] State residual risks and anything intentionally not included.
