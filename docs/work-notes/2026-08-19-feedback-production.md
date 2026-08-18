# Feedback production rollout

## Objective

Add an account-free, text-only feedback path that works from TrainMe Settings without requiring GitHub, while keeping submitted content private and the public repository indicator aggregate-only.

## Scope and decisions

- The Android app opens a lightweight Worker form in the current interface locale and adds only app version, Android version, and interface locale.
- The form supports `en`, `ru`, `cs`, `sk`, `sr-Cyrl`, and `sr-Latn`; reply contact is optional.
- Images and R2 are not included.
- Cloudflare Turnstile is loaded only after local validation. The Worker verifies the exact production hostname and `feedback` action before storing a message.
- D1 holds private text/contact; no public listing route exists. The public Shields endpoint returns only the number of rows awaiting review.
- A scheduled Worker job removes rows around 180 days after creation.

## Production resources

- Worker: `https://trainme-feedback.queukat.workers.dev`
- D1: `trainme-feedback`
- Worker schedule: `0 4 * * *` UTC
- Android release: `1.0.9` (`versionCode` 128), Google Play production track

Secrets are stored only as Worker/maintainer bindings and are not recorded here.

## Verification ledger

- Worker typecheck and 14/14 unit tests passed.
- Wrangler dry-run and production deployment passed; D1 reports no pending migrations.
- Live health, config, badge, security-header, and exact-host checks passed.
- A real Chromium Turnstile submission returned `201`, was read back from D1, and the uniquely marked synthetic row was then deleted.
- Mobile WebKit/iPhone rendering, narrow-screen controls, and preserved-form failure recovery were checked. A CSS descendant-selector defect in the feedback-type buttons was found and fixed during this pass.
- `:app:testDebugUnitTest` passed and `:app:assembleDebug` succeeded.
- Full `lintDebug` remains blocked by the repository's pre-existing localization debt; the release-specific `lintVitalRelease` passed.
- Signed AAB validation and upload passed through the Play production rail; Play readback returned version code `128` in `production`.

## Remaining risk

Turnstile depends on Cloudflare reachability. If it cannot finish, the form now stops waiting after 45 seconds, keeps the message, and offers a localized retry instead of remaining busy indefinitely.
