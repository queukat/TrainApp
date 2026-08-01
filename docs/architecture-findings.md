# Architecture Findings

This ledger records source-backed architectural constraints that affect dependent work. Documentation of a finding is not the same as fixing it.

## 2026-08-01 documentation audit

| ID | Finding | Impact | Current disposition |
| --- | --- | --- | --- |
| AF-001 | Room uses `fallbackToDestructiveMigration(dropAllTables = true)`. | A future schema-version change can drop Room-managed cached data instead of migrating it. | Documented; add explicit migrations before changing the schema version. |
| AF-002 | The `route_info` table/update function has no active runtime population caller. | It cannot be treated as a persistent route/full-route cache. | Documented as inactive; wire intentionally or remove in a separate code change. |
| AF-003 | Cumulative-route reconstruction has no current UI call site and, if wired, caches data only in process memory. | The path is not currently passenger-accessible and would need a new fetch after process death or TTL expiry. | Documented; UI wiring and persistent caching are not included in this documentation task. |
| AF-004 | Saved routes and recents are JSON-backed SharedPreferences records, not Room entities. | Storage ownership and migration behavior differ from the station database. | Documentation corrected; future persistence work must preserve legacy preference migration. |
| AF-005 | Scheduled reminder alarms are not restored after reboot. | A reminder selected before a reboot may not fire afterward. | Documented as a product/runtime limitation; boot rescheduling is separate work. |
| AF-006 | Repository, real Room migration/DAO, and ViewModel orchestration have limited direct automated coverage. | Cache, migration, and orchestration regressions rely heavily on compile/manual/live-device evidence. | Documented in `docs/testing.md`; test implementation is separate work. |

## Closure rule

When a finding is fixed, add the implementing commit, verification evidence, compatibility/migration result, and any remaining risk. Do not delete historical findings.
