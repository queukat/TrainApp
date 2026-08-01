# TrainMe Testing and Quality Guide

_Last reviewed: 2026-08-01_

TrainMe has local unit, instrumented/runtime, screenshot, static-analysis, lint, and unit-coverage surfaces. There is no tracked GitHub Actions workflow, so a green local verification ledger is the primary handoff evidence.

## Verification levels

| Level | Command | Use when |
| --- | --- | --- |
| Focused unit | `.\gradlew.bat :app:testDebugUnitTest --console=plain` | Normal model, mapping, persistence-helper, and time changes. |
| Debug compile | `.\gradlew.bat :app:assembleDebug --console=plain` | Source/resource changes that need Android compilation. |
| Static checks | `.\gradlew.bat :app:detekt :app:ktlintCheck --console=plain` | Kotlin changes and pre-handoff quality review. |
| Android lint | `.\gradlew.bat :app:lintDebug --console=plain` | Manifest, resource, API-level, and Android integration changes. |
| Unit coverage | `.\gradlew.bat :app:jacocoDebugUnitTestReport --console=plain` | Coverage investigation or Sonar preparation. |
| Instrumented | `.\gradlew.bat :app:connectedDebugAndroidTest --console=plain` | Full connected suite, including the live-service screenshot generator; use only on a controlled device/emulator. |

These commands are derived from the tracked build configuration. Record only commands actually executed in a handoff.

## Unit-test map

| Area | Main tests | What they establish |
| --- | --- | --- |
| API/model contracts | `TrainApiModelTest` | Serialized names, direct/connected response shapes, empty connected form, segments, station-language fallbacks. |
| Database models | `DbModelTest` | Localized stop names, route metadata, crossing-stop filtering, route-name fallback. |
| Repository result contracts | `RepositoryResultModelsTest` | Result payload types, not repository network/cache orchestration. |
| Route presentation | `RouteSearchMappingTest`, `ConnectedRouteSupportTest`, `RouteBadgesTest` | Result/error mapping, transfer-leg selection and risk, route badges. |
| Continuity helpers | `SavedRouteSupportTest`, `RecentSearchSupportTest` | Saved-route migration/localization and recent-search dedupe/limit behavior. |
| UI state models | `UiStateModelsTest` | State payload construction. |
| Time | `DateTimeUtilsTest` | Montenegro timezone parsing, invalid input, countdown, and time ranges. |

`ExampleUnitTest` is a generated arithmetic placeholder and must not be cited as meaningful project coverage.

## Instrumented and runtime checks

| Test surface | Scope | Constraint |
| --- | --- | --- |
| `SettingsLanguageLabelsTest` | Human-readable language labels and stored preference values. | Requires device/emulator. |
| `CommuterRepeatFlowTest` | Live search plus saved/recent continuity across restart. | Calls the live timetable API; unavailable service can skip the journey. |
| `ReminderRuntimeVerificationTest` | Permission-denied and exact-alarm recovery paths. | Uses Android system/app-ops behavior; actual delivery case is manually ignored. |
| `StoreScreenshotTest` | Home, results, expanded route, and settings captures across locales. | Artifact generator using live data, not a stable regression suite. |
| `ExampleInstrumentedTest` | Package-context check. | Generated smoke placeholder. |

`RuntimeTestSupport` talks directly to the live timetable service and uses assumptions. A skipped live test is evidence that its prerequisite was unavailable, not evidence that the journey passed.

The default `connectedDebugAndroidTest` discovery also includes `StoreScreenshotTest`; it is not an isolated stable functional-regression command. Use an Android test-runner class/filter or add a dedicated Gradle task when only a targeted instrumented class should run. The live-search and notification-denial paths can also skip when their timetable prerequisite is unavailable.

## Screenshot workflow

Screenshot tests write capture artifacts for Play listing preparation. Treat them separately from functional regression tests because results depend on live data, locale, emulator state, and stable filenames. When the screenshot generator or README asset names change, verify that emitted names match tracked listing files.

## Quality tooling

- Detekt 1.23.8 with `config/detekt/detekt.yml`; warnings are not configured as build-fatal by that file.
- ktlint Gradle plugin 14.2.0 in Android mode.
- Android lint is configured, but dependency-version, old-target, and AGP-version checks are disabled in the app build.
- JaCoCo 0.8.13 reports unit-test coverage only.
- SonarQube plugin 7.2.2.6593 consumes the JaCoCo XML, with broad exclusions for activities, network/database/repository paths, most Compose screens, ViewModel, reminders, locales, and themes.

The Sonar percentage therefore represents a narrow included slice; it must not be presented as whole-application coverage.

## Known coverage gaps

- `TrainRepository` refresh/cache/error behavior has no direct unit test.
- Real Room DAOs, schema migration, and destructive-migration behavior lack integration coverage.
- Retrofit/HTTP boundary behavior is not tested against a controlled fake server.
- `TrainViewModel` orchestration has no direct unit suite.
- Successful notification delivery and reboot rescheduling are not automated.
- Compose semantics/accessibility and visual regression are not systematically tested.
- Locale resource completeness has no dedicated Gradle verification task.
- Live-service instrumented tests can skip when their external prerequisite is unavailable.

These are documentation of coverage boundaries, not claims that the corresponding behavior is broken.

## Change-to-check guide

| Change | Minimum relevant verification |
| --- | --- |
| Pure model/helper | Focused unit tests. |
| Repository/API/cache | Unit tests plus debug compile; add a controlled boundary test when behavior changes. |
| Room schema/entity | Unit tests, debug compile, migration/DAO instrumentation, and data-preservation review. |
| Compose UI | Debug compile plus device/emulator flow; refresh screenshots if public presentation changes. |
| Strings/locales | Debug compile, affected-locale review, placeholder parity, and screenshot check where visible. |
| Reminder/permissions | Unit/debug checks plus targeted device flow on relevant Android versions. |
| Release configuration | Signing/Play validation path from the release documentation; never publish as an incidental test. |
