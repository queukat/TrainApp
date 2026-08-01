# Security and Data Handling

_Last reviewed: 2026-08-01_

This document describes behavior visible in the tracked Android source and build configuration. It is not a claim about server-side logging, retention, hosting, legal compliance, or third-party application policy.

TrainMe is an independent passenger application. It is not an official ZPCG ticketing, support, payment, or account service.

## Permissions and external mechanisms

| Capability | Permission or mechanism | Purpose |
| --- | --- | --- |
| Timetable access | `INTERNET` | Retrieves stations and journeys from `https://api.zpcg.me/`. |
| Notification reminder | `POST_NOTIFICATIONS` | Optional runtime permission for a reminder selected by the passenger. |
| Timely reminder | `SCHEDULE_EXACT_ALARM` | Optional special access on Android versions/devices that require it. |
| Calendar handoff | `ACTION_INSERT` intent | Opens a draft event in another calendar application; TrainMe does not request calendar read/write permission. |
| Map handoff | `geo:` intent | Opens a compatible maps application with a selected station label and coordinates. |

TrainMe does not request location, contacts, calendar, storage, camera, microphone, or phone permissions.

## Data kept on the device

TrainMe uses private application storage for:

- station names, identifiers, types, and coordinates downloaded from the timetable service;
- a route-metadata schema defined by the local data layer, although the current runtime has no active population path for that table;
- station-language, reminder, lead-time, and countdown-refresh preferences;
- saved routes;
- up to five recent route searches, including endpoints and timestamp.

Room stores the station catalogue and defines the currently inactive `route_info` table. SharedPreferences stores settings, saved routes, recent searches, and cache timestamps. This data is not protected by application-level encryption. Android cloud backup and device-transfer extraction are disabled for the app.

## Data leaving the app

During route search, the selected origin, destination, and travel date are sent to `https://api.zpcg.me/` as timetable request parameters. The app processes the returned stations and routes locally.

Optional user actions disclose limited context to Android or another selected application:

- a notification can display train number, departure station, and reminder lead time;
- calendar handoff includes a draft title with train number and selected journey endpoints;
- maps handoff includes station name and coordinates.

The timetable API, Android system services, calendar application, and maps application operate under their own data practices.

## Network boundary

- Cleartext traffic is disabled by the application network-security configuration.
- HTTPS uses Android system trust anchors.
- Certificate pinning is not configured.
- The repository cannot establish whether the timetable service logs requests or how long it retains them.

Do not document “no server logs”, a retention period, a hosting jurisdiction, or an uptime guarantee without evidence from the service operator.

## Reminder boundary

- Reminder broadcasts use a non-exported receiver.
- Pending intents are immutable.
- A reminder is scheduled only after a passenger chooses that path.
- Calendar and map transfers occur only after the corresponding user action.
- Scheduled alarms are not restored after reboot by the current implementation.

## Analytics and accounts

The active Android build does not wire an analytics or crash-reporting SDK. Firebase entries in the version catalogue are not active app dependencies. This is build-configuration evidence, not a guarantee about the behavior of the remote timetable service or other applications.

TrainMe does not implement account registration, ticket purchase, payments, continuous location tracking, or direct calendar-provider writes.

## Release security

Release signing values come from ignored local `keystore.properties` configuration or `TRAINAPP_*` environment variables. Keystores, passwords, Play service-account files, and generated artifacts must never be committed.

The current release documentation records a historical risk that an older signing key may have been exposed. Treat that key as compromised and use a rotated key for trusted delivery.

## Operational risks

- Ordinary private app storage does not protect data on a compromised or rooted device.
- Notification text may be visible on a lock screen according to Android settings.
- Exported launch activity extras are not an authenticated private API.
- Operational logging must not be expanded to include route choices, secrets, or raw server bodies.
- External timetable accuracy, privacy, and availability remain outside the app’s trust boundary.

## Reporting a security issue

Do not publish suspected vulnerabilities, credentials, or passenger data in a public issue. Contact the repository owner through a private channel and provide the affected version, concise reproduction steps, impact, and a safe proof of concept. If no private channel is published, request one without including exploit details.
