# Documentation Refresh — 2026-08-01

## Objective

Rebuild project documentation from current source evidence after the move to Google Play-only distribution and removal of GitHub release/telemetry surfaces.

## Audit split

Eight read-only Terra audits covered:

- architecture and runtime data flows;
- product/UI behavior and accessibility;
- Play release governance;
- tests and quality tooling;
- public claims and licensing boundaries;
- documentation inventory and information architecture;
- security, privacy, permissions, and external handoffs;
- canonical build/configuration facts.

No audit agent edited files or ran Gradle.

## Decisions

- Keep README as the public product entry point and move technical detail into `docs/`.
- Document Google Play as the public distribution rail; do not restore GitHub release automation.
- Call AlarmManager-based reminders “notification reminders”, not server push.
- Split Room facts from SharedPreferences continuity facts.
- Document the destructive Room migration fallback as a current risk.
- Treat live timetable/API behavior and third-party privacy practices as external assumptions, not guarantees.
- Keep the maintainer release runner private/ignored while documenting its required control contract.
- Do not add a `verifyTranslations` command until a real Gradle task exists.

## Mandatory factual corrections

- Target SDK: 36, not 35.
- App UI locales: English, Russian, Czech, Slovak, Serbian Cyrillic, and Serbian Latin.
- Station-name choices: English, Montenegrin Latin, and Montenegrin Cyrillic.
- Saved/recent routes: SharedPreferences, not Room.
- Cumulative route cache: process memory, approximately 12-hour TTL.
- Stops refresh: approximately 24 hours.
- Route-query `start`/`finish` values: station API route names, not numeric stop IDs.
- Room schema: version 1 with destructive fallback.
- No tracked GitHub Actions workflow.

## Verification ledger

```text
PASS | git diff --check | no whitespace errors
PASS | relative Markdown link audit | 14 Markdown files; every local target exists
PASS | stale-claim scan | no targetSdk 35, push-reminder, Room-continuity, or private-rail wording remains in public docs/listing copy
PASS | source fact check | SDK/version, preference keys, recent-search limit, cache TTLs, and destructive migration matched tracked source
PASS | Play full-description review | six locale files remain below 4,000 characters; obsolete push wording removed
PASS | independent Terra diff review | architecture, product, release, testing, security, configuration, public-copy, and IA findings addressed
SKIP | Gradle build/tests | documentation and listing-copy task; no app behavior changed
```

Residual limitations:

- No external verification of Google Play availability, ZPCG policy, or timetable-service privacy/retention.
- Localized replacement copy was reviewed for scope and terminology, not by independent native-language reviewers.
- Architecture findings are documented but not fixed by this task.
