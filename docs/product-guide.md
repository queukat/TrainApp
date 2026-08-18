# TrainMe Product Guide

_Last reviewed: 2026-08-19_

TrainMe is an independent passenger application for planning rail journeys in Montenegro. It is not the official timetable, ticketing product, payment service, or support channel of ZPCG AD Podgorica.

## Plan a journey

1. Choose an origin and destination from the station suggestions.
2. Select the travel date.
3. Start the search.
4. Review direct and connected options.
5. Expand a route for stops, timings, and available map handoffs.
6. Optionally save the journey or create a departure reminder.

The station fields search across English, Montenegrin Latin, and Montenegrin Cyrillic names. A typed label must resolve to a station in the current catalogue; unmatched free text cannot be submitted as a route endpoint.

## Route surfaces

### Direct journeys

A direct route card can show:

- train and endpoint labels;
- departure, arrival, duration, and countdown;
- fare information when supplied by the timetable response;
- international-service status;
- intermediate stops with arrival/departure state;
- a maps handoff for stops that have coordinates.

### Connected journeys

Connected cards show two legs, the interchange station, total duration, and interchange waiting-time guidance. When a reminder is created from a connected card, it applies to the first departure leg.

The interchange guidance is a presentation aid derived from the returned schedule. It is not a connection guarantee and does not account for live disruption.

## Saved routes and recent searches

- A journey can be saved after both endpoints resolve to known stations.
- Saved cards repeat the journey using the current station catalogue.
- Recent searches are deduplicated and limited to five.
- Repeating a saved or recent journey starts a new timetable request; stored data is not a frozen schedule.

Saved routes, recent searches, and preferences stay in the app’s private local storage until the app data is cleared. Android backup and device-transfer extraction are disabled for TrainMe data.

## Reminders

The reminder dialog supports:

- on-device notification reminder;
- calendar handoff;
- both paths;
- no reminder.

The passenger can choose the lead time and save a default action in Settings.

### Notification path

TrainMe schedules a local Android alarm. Depending on Android version and device policy, notification permission and exact-alarm access may be required. Missing permission, an unparseable departure, a reminder that is too late, or a scheduling failure is shown as an operational status rather than silently treated as success.

Notifications may expose the train number, departure station, and lead time on the device’s notification surface. Android notification privacy settings control how that content appears on a locked or shared device.

### Calendar path

TrainMe opens a draft event in a compatible calendar application. The passenger reviews and saves the event there. TrainMe does not request calendar read/write permission and does not create the event silently.

## Settings

The settings surface controls:

- station display language: English, Montenegrin Latin, or Montenegrin Cyrillic;
- default reminder action;
- default reminder lead time;
- automatic time-to-departure refresh;
- test notification actions;
- an account-free feedback form for problems and ideas;
- independence/disclaimer and support links.

Station-name language is separate from the Android interface locale. The app resources support English, Russian, Czech, Slovak, Serbian Cyrillic, and Serbian Latin interfaces.

The feedback action opens a lightweight web form in the current interface language and attaches only the TrainMe version, Android version, and interface locale. A reply contact is optional. The form does not attach saved routes or recent-search history, and it does not accept images in this release.

## Operational states

| State | Meaning | Passenger action |
| --- | --- | --- |
| Cached station warning | The latest station refresh failed, but an existing catalogue is available. | Continue with the cache; reopen the app later to attempt another automatic refresh. |
| Station catalogue unavailable | No usable catalogue is available. | Restore connectivity and reopen the app later. |
| Station selection error | One or both typed endpoints are not resolved stations. | Select from suggestions. |
| No results | The request completed but returned no matching journey. | Review endpoints/date or try another date. |
| Network error | The timetable service could not be reached. | Check connectivity, then run Search again. |
| Server error | The remote service returned an error response. | Run Search again later. |
| Invalid response | The returned data could not form a supported route result. | Run Search again later and report reproducible cases. |
| Reminder permission outcome | Android blocked or requires action for the selected reminder path. | Follow the shown permission/settings action. |

## Freshness and service boundaries

- The station catalogue is normally refreshed after roughly 24 hours.
- Journey search depends on `https://api.zpcg.me/` and has a 10-second application timeout.
- TrainMe does not provide ticket purchases, account registration, live vehicle position, guaranteed connection protection, or guaranteed remote-service availability.

For official schedules, tickets, service notices, and company information, use the official ZPCG channel referenced in the main [README](../README.md#independence-notice).
