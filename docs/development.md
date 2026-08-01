# TrainMe Development Guide

_Last reviewed: 2026-08-01_

This guide describes the tracked Android project. Release credentials, local SDK configuration, generated artifacts, and maintainer-only tooling must remain untracked.

## Requirements

- Windows PowerShell for the commands shown below, or equivalent shell syntax.
- JDK 17.
- Android SDK 36 with a compatible Android Studio installation.
- Git.
- An Android device or emulator for instrumented and runtime UI checks.

The repository uses the Gradle 8.13 wrapper, Kotlin 2.1.10, and Android Gradle Plugin 8.9.3. Do not substitute a globally installed Gradle for the wrapper.

## Project facts

| Setting | Value |
| --- | --- |
| Root project | `train` |
| Module | `:app` |
| Namespace / application ID | `com.queukat.train` |
| Minimum SDK | 24 |
| Target SDK | 36 |
| Compile SDK | 36 |
| Java and Kotlin JVM target | 17 |
| Default version | `versionCode 127`, `versionName 1.0.8` |
| UI | Jetpack Compose + Material 3 |
| Persistence | Room + SharedPreferences |
| Network | Retrofit + Gson + OkHttp |

`VERSION_CODE` and `VERSION_NAME` environment variables can override the default version during a build.

## First setup

```powershell
git clone https://github.com/queukat/TrainApp.git TrainMe
Set-Location TrainMe
.\gradlew.bat :app:assembleDebug --console=plain
```

Create `local.properties` through Android Studio or point it at the local Android SDK. Do not commit machine-specific SDK paths.

## Common commands

```powershell
# Debug build
.\gradlew.bat :app:assembleDebug --console=plain

# Install on a connected device or emulator
.\gradlew.bat :app:installDebug --console=plain

# Unit tests
.\gradlew.bat :app:testDebugUnitTest --console=plain

# Static analysis and Android lint
.\gradlew.bat :app:detekt :app:ktlintCheck :app:lintDebug --console=plain

# Unit-test coverage report
.\gradlew.bat :app:jacocoDebugUnitTestReport --console=plain

# Instrumented tests
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

The repository does not currently define a `verifyTranslations` task. Do not make it part of a required checklist unless a real Gradle task is added and verified.

## Source map

```text
app/src/main/java/com/queukat/train/
|- MainActivity.kt          application entry and launch arguments
|- SettingsActivity.kt      preference entry point
|- data/api/                Retrofit timetable contract
|- data/model/              Gson response and preference models
|- data/db/                 Room entities, DAOs, and database
|- data/repository/         acquisition, cache, and response enrichment
|- ui/                      Compose surfaces and UI state/presentation
|- util/                    time, reminders, notifications, intents, locales
```

See [Architecture](architecture.md) for ownership boundaries and data flows.

## Runtime configuration

### Timetable service

The Retrofit base URL is `https://api.zpcg.me/`. Route search sends selected origin, destination, and date. Avoid introducing a second parser or a second source of endpoint constants outside the API boundary.

### Languages

Android interface locales declared by the application are:

- `en`
- `ru`
- `cs`
- `sk`
- `sr-Cyrl`
- `sr-Latn`

Station-name preferences are a different domain and use `en`, `me`, and `meCyr`.

Play metadata locales are also separate: `en-GB`, `en-US`, `ru-RU`, `sr`, `cs-CZ`, and `sk` are used for current changelogs. Do not infer app-resource coverage from the Play directory names.

## Local persistence

- Room database: `zpcg.db`, schema version 1.
- Room tables: `stops`; `route_info` is defined but has no active runtime population path.
- Preferences file: `train_prefs`.
- Saved and recent journeys live in preferences, not Room.
- Stops refresh after roughly 24 hours; cumulative route data is an in-memory cache with roughly 12-hour TTL.

The current Room configuration permits destructive migration. Any schema-version increase must include an explicit review of user-data preservation before release.

## Signing and release configuration

Debug work does not require release credentials. Production signing uses an ignored `keystore.properties` file or `TRAINAPP_*` environment variables. Never commit keystores, service-account files, passwords, or generated release artifacts.

See [Release setup](release_setup.md) and [Play release rail](play_release.md). Google Play is the public distribution channel; the repository has no GitHub Actions release workflow.

## Contribution boundaries

- Start by checking `git status --short`; preserve unrelated work.
- Keep parsing at external input boundaries.
- Prefer typed domain values, especially for money and time.
- Do not hide architectural defects behind suppressions or compatibility adapters.
- Update affected localized strings and Play copy when a user-visible contract changes.
- Record the exact verification commands that were run; do not describe skipped live-service tests as passes.
