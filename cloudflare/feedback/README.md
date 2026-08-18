# TrainMe feedback

This directory contains TrainMe's account-free, text-only feedback surface: the localized static form, a Cloudflare Worker, and a D1 schema. It has no public endpoint for reading submitted text or contact details.

## Preview locally

From the repository root:

```powershell
python -m http.server 4173 --bind 127.0.0.1 --directory cloudflare/feedback/public
```

Then open `http://127.0.0.1:4173/`.

The form supports the same six interface locales as the Android app and uses the production API contract below. Running only the static directory previews layout and local validation; Turnstile and submission require the Worker runtime.

## Backend contract

The production implementation keeps public and private surfaces separate:

| Route | Behaviour |
| --- | --- |
| `GET /` | Static form assets, passed through the Worker asset binding. |
| `GET /api/config.json` | Returns only `{ "turnstileSiteKey": "…" }`; it never exposes the verification secret. |
| `POST /api/feedback` | Same-origin JSON submission, a 10/minute per-client burst gate, strict allow-list validation, mandatory UUID v4 `Idempotency-Key`, production Turnstile verification, and one prepared D1 insert. |
| `GET /badge.json` | Shields endpoint JSON with the aggregate count of rows whose status is `new`. |
| `GET /healthz` | Checks that D1 can answer a minimal query. |

`POST /api/feedback` accepts only same-origin `application/json` and rejects unknown fields. It accepts this JSON object:

```json
{
  "type": "problem",
  "message": "The transfer time is unclear.",
  "contact": "optional@example.com",
  "turnstileToken": "token from the Turnstile widget",
  "appVersion": "1.2.3",
  "androidVersion": "14",
  "uiLocale": "ru"
}
```

`type` is `problem`, `idea`, or `other`; a trimmed message must contain 3–2,000 Unicode code points; contact is optional and limited to 200. Repeating the same key and payload returns the original submission id with `duplicate: true`; reusing a key for a different payload returns `409 idempotency_conflict`.

The Worker neither writes nor returns request IP addresses, Turnstile tokens, or message/contact values in logs. It does not claim complete anonymity: Cloudflare account-level request logging and retention are separate platform controls and should be configured by the account owner as required.

The submit limiter hashes `CF-Connecting-IP` only to create an in-memory rate-limit key; it neither persists nor logs that IP. If Cloudflare does not supply the header, the idempotency key becomes the fallback. The limiter is intentionally a short abuse burst control, not an identity system: Workers limits are local to an edge location and shared mobile/NAT/proxy IPs can occasionally share the 10-per-minute budget.

Example public badge payload:

```json
{
  "schemaVersion": 1,
  "label": "feedback inbox",
  "message": "3 awaiting review",
  "color": "orange",
  "cacheSeconds": 300
}
```

The README badge uses:

```markdown
[![Feedback awaiting review](https://img.shields.io/endpoint?url=https%3A%2F%2Ftrainme-feedback.queukat.workers.dev%2Fbadge.json)](https://trainme-feedback.queukat.workers.dev)
```

This is a feedback-workflow indicator, not usage analytics, distribution telemetry, or a GitHub Actions status.

## Initial private review

Before an administrative UI exists, new messages can be listed with a bounded D1 query:

```powershell
wrangler d1 execute trainme-feedback --remote --command "SELECT id, created_at, type, message, contact, app_version, android_version, ui_locale FROM feedback WHERE status = 'new' ORDER BY created_at DESC LIMIT 50"
```

The Worker configuration pins the Queukat account, the `trainme-feedback` D1 database, and the production hostname `trainme-feedback.queukat.workers.dev`. The submit limiter reserves namespace `615247981` for this Worker; do not reuse that namespace elsewhere in the Queukat account. Preview URLs are disabled so the exact production hostname remains the Turnstile trust boundary.

## Local checks and deployment preparation

```powershell
Set-Location cloudflare/feedback
npm install
npm run check
Copy-Item .dev.vars.example .dev.vars
```

Set the development values in `.dev.vars`. For production, set `TURNSTILE_SECRET_KEY` and `TURNSTILE_SITE_KEY` as Worker secrets; `TURNSTILE_EXPECTED_HOSTNAME` and `TURNSTILE_EXPECTED_ACTION=feedback` are pinned in `wrangler.jsonc`. `TURNSTILE_SITE_KEY` is deliberately returned by `/api/config.json`; the other values are server-side only. Production rejects feedback if the verification secret, expected hostname, or expected action is absent, so an accidental insecure deployment fails closed.

Apply migrations explicitly before deployment:

```powershell
wrangler d1 migrations apply trainme-feedback --remote
wrangler deploy
```

Do not put a production Turnstile secret in `wrangler.jsonc`, `.dev.vars`, source, or Git.

## Retention

The scheduled Worker handler deletes feedback rows around 180 days after creation (`0 4 * * *` UTC). Its timestamp creation and comparison both use SQLite timestamps, and daily scheduling means deletion can occur up to about one day after the 180-day threshold. This is deliberately database-only: no submission content is copied to analytics, KV, or an inbox endpoint. Confirm the retention period against the project's privacy notice before the first public deployment.

## Private review

Use the bounded maintainer command only in a trusted terminal; it prints the private message and optional contact fields:

```powershell
.\scripts\list-new-feedback.ps1 -Limit 50
```

Add `-Local` to inspect the local D1 emulator. There is intentionally no public admin or listing route.

## Weight and compatibility target

- no external fonts, images, frameworks, or JavaScript packages;
- safe-area and text-size handling for mobile Safari;
- 16 px form controls to prevent iOS focus zoom;
- graceful fallbacks for newer CSS color functions;
- text-only submissions in the first release.
