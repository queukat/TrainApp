# TrainMe Documentation

This directory separates public product positioning from technical and operational documentation. Facts that can drift should be verified against the referenced source files before release.

| Document | Audience | Purpose |
| --- | --- | --- |
| [Architecture](architecture.md) | Engineers and reviewers | Runtime topology, data flows, persistence, caches, external contracts, and known limitations. |
| [Product guide](product-guide.md) | Product, support, QA | Passenger flows, route surfaces, reminders, settings, and operational states. |
| [Development](development.md) | Contributors | Environment, project layout, build configuration, local setup, and common commands. |
| [Testing](testing.md) | Engineers and QA | Unit, instrumented, live-service, screenshot, and quality-tooling coverage. |
| [Security and data handling](security-privacy.md) | Engineers and reviewers | Permissions, local data, external handoffs, transport security, and trust boundaries. |
| [Developer checklist](dev_checklist.md) | Contributors | Short pre-change, verification, localization, UI, and handoff checklist. |
| [Release setup](release_setup.md) | Release maintainers | Signing material, unsigned/signed build boundaries, and key hygiene. |
| [Play release rail](play_release.md) | Release maintainers | Play validation, upload intent, changelogs, tracks, and artifact checks. |
| [Play listing assets](play_assets.md) | Release and design maintainers | Listing text, screenshots, feature graphics, locale boundaries, and safe publication. |
| [Presentation style](presentation_style.md) | Public-copy authors | Product language, independence boundary, and claim discipline. |
| [Architecture findings](architecture-findings.md) | Engineers and maintainers | Source-backed architectural constraints and their disposition. |

## Sources of truth

When documentation and implementation disagree, use this order while correcting the docs:

1. Gradle and Android configuration for build facts.
2. Kotlin source and resources for runtime behavior.
3. Fastlane metadata for current Play listing copy and localized changelogs.
4. Documentation for intended operating policy.

External service behavior, retention, Play availability, and ZPCG operational policy cannot be proven from this repository. Document those as external dependencies or assumptions, not guarantees.

## Scope boundary

TrainMe is an independent passenger application. It is not an official ZPCG timetable, ticketing product, support channel, or payment service. Google Play is the project’s public Android distribution channel; debug and unsigned artifacts are development outputs, not public releases.
