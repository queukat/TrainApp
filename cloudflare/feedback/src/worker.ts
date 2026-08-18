interface Env {
  ASSETS: Fetcher;
  DB: D1Database;
  FEEDBACK_SUBMIT_RATE_LIMIT: RateLimit;
  ENVIRONMENT: string;
  TURNSTILE_SITE_KEY?: string;
  TURNSTILE_SECRET_KEY?: string;
  TURNSTILE_EXPECTED_HOSTNAME?: string;
  TURNSTILE_EXPECTED_ACTION?: string;
}

type FeedbackType = "problem" | "idea" | "other";

interface FeedbackPayload {
  type: FeedbackType;
  message: string;
  contact: string | null;
  turnstileToken: string;
  appVersion: string | null;
  androidVersion: string | null;
  uiLocale: string | null;
}

interface ExistingSubmission {
  id: string;
  request_hash: string;
}

const MAX_JSON_BYTES = 8_192;
const MAX_MESSAGE_CODE_POINTS = 2_000;
const MAX_CONTACT_CODE_POINTS = 200;
const ALLOWED_FIELDS = new Set([
  "type",
  "message",
  "contact",
  "turnstileToken",
  "appVersion",
  "androidVersion",
  "uiLocale",
]);
const IDEMPOTENCY_KEY_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const STATIC_SECURITY_HEADERS: Record<string, string> = {
  "content-security-policy": "default-src 'self'; script-src 'self' https://challenges.cloudflare.com; frame-src https://challenges.cloudflare.com; connect-src 'self' https://challenges.cloudflare.com; style-src 'self'; img-src 'self' data:; font-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'; object-src 'none'",
  "permissions-policy": "accelerometer=(), autoplay=(), camera=(), geolocation=(), microphone=(), payment=(), usb=()",
  "referrer-policy": "no-referrer",
  "x-content-type-options": "nosniff",
};

function getBadgeCache(): {
  match(request: Request): Promise<Response | undefined>;
  put(request: Request, response: Response): Promise<void>;
} {
  return (globalThis.caches as unknown as {
    default: {
      match(request: Request): Promise<Response | undefined>;
      put(request: Request, response: Response): Promise<void>;
    };
  }).default;
}

class HttpError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
  ) {
    super(code);
  }
}

function responseJson(value: unknown, status = 200, headers?: HeadersInit): Response {
  const responseHeaders = new Headers({
    "content-type": "application/json; charset=utf-8",
    "x-content-type-options": "nosniff",
    "referrer-policy": "no-referrer",
    ...headers,
  });
  return new Response(JSON.stringify(value), { status, headers: responseHeaders });
}

function errorResponse(error: HttpError): Response {
  const headers: Record<string, string> = { "cache-control": "no-store" };
  if (error.code === "rate_limited") headers["retry-after"] = "60";
  return responseJson(
    { ok: false, error: { code: error.code } },
    error.status,
    headers,
  );
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function codePointLength(value: string): number {
  return Array.from(value).length;
}

function optionalText(value: unknown, field: string, maxLength: number): string | null {
  if (value === undefined) return null;
  if (typeof value !== "string") throw new HttpError(400, `invalid_${field}`);

  const normalized = value.trim();
  if (codePointLength(normalized) > maxLength) throw new HttpError(400, `invalid_${field}`);
  return normalized || null;
}

function parseFeedbackPayload(value: unknown): FeedbackPayload {
  if (!isRecord(value)) throw new HttpError(400, "invalid_json");
  for (const key of Object.keys(value)) {
    if (!ALLOWED_FIELDS.has(key)) throw new HttpError(400, "unknown_field");
  }

  if (value.type !== "problem" && value.type !== "idea" && value.type !== "other") {
    throw new HttpError(400, "invalid_type");
  }
  if (typeof value.message !== "string") throw new HttpError(400, "invalid_message");
  const message = value.message.trim();
  if (codePointLength(message) < 3 || codePointLength(message) > MAX_MESSAGE_CODE_POINTS) {
    throw new HttpError(400, "invalid_message");
  }
  if (typeof value.turnstileToken !== "string" || value.turnstileToken.length === 0 || value.turnstileToken.length > 2_048) {
    throw new HttpError(400, "invalid_turnstile_token");
  }

  return {
    type: value.type,
    message,
    contact: optionalText(value.contact, "contact", MAX_CONTACT_CODE_POINTS),
    turnstileToken: value.turnstileToken,
    appVersion: optionalText(value.appVersion, "app_version", 64),
    androidVersion: optionalText(value.androidVersion, "android_version", 64),
    uiLocale: optionalText(value.uiLocale, "ui_locale", 16),
  };
}

async function readJson(request: Request): Promise<unknown> {
  const declaredLength = request.headers.get("content-length");
  if (declaredLength !== null && (!/^\d+$/.test(declaredLength) || Number(declaredLength) > MAX_JSON_BYTES)) {
    throw new HttpError(413, "payload_too_large");
  }

  if (!request.body) throw new HttpError(400, "invalid_json");
  const reader = request.body.getReader();
  const decoder = new TextDecoder("utf-8", { fatal: true });
  let totalBytes = 0;
  let text = "";
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      totalBytes += value.byteLength;
      if (totalBytes > MAX_JSON_BYTES) {
        await reader.cancel();
        throw new HttpError(413, "payload_too_large");
      }
      text += decoder.decode(value, { stream: true });
    }
    text += decoder.decode();
    return JSON.parse(text) as unknown;
  } catch (error) {
    if (error instanceof HttpError) throw error;
    throw new HttpError(400, "invalid_json");
  }
}

function requireIdempotencyKey(request: Request): string {
  const key = request.headers.get("idempotency-key") ?? "";
  if (!IDEMPOTENCY_KEY_PATTERN.test(key)) throw new HttpError(400, "invalid_idempotency_key");
  return key.toLowerCase();
}

function requireSameOrigin(request: Request): void {
  const origin = request.headers.get("origin");
  if (origin !== new URL(request.url).origin) throw new HttpError(403, "origin_not_allowed");
}

async function sha256(value: string): Promise<string> {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function enforceSubmitRateLimit(request: Request, env: Env, idempotencyKey: string): Promise<void> {
  const clientIp = request.headers.get("cf-connecting-ip")?.trim();
  const subject = clientIp ? `ip:${clientIp}` : `idempotency:${idempotencyKey}`;
  const key = await sha256(`trainme-feedback-submit-v1:${subject}`);
  const outcome = await env.FEEDBACK_SUBMIT_RATE_LIMIT.limit({ key });
  if (!outcome.success) throw new HttpError(429, "rate_limited");
}

function submissionFingerprint(payload: FeedbackPayload): string {
  return JSON.stringify({
    type: payload.type,
    message: payload.message,
    contact: payload.contact,
    appVersion: payload.appVersion,
    androidVersion: payload.androidVersion,
    uiLocale: payload.uiLocale,
  });
}

async function verifyTurnstile(env: Env, token: string, idempotencyKey: string): Promise<boolean> {
  const secret = env.TURNSTILE_SECRET_KEY?.trim();
  if (!secret) {
    if (env.ENVIRONMENT === "production") throw new HttpError(503, "turnstile_not_configured");
    return true;
  }

  const expectedHostname = env.TURNSTILE_EXPECTED_HOSTNAME?.trim();
  const expectedAction = env.TURNSTILE_EXPECTED_ACTION?.trim();
  if (env.ENVIRONMENT === "production" && (!expectedHostname || !expectedAction)) {
    throw new HttpError(503, "turnstile_not_configured");
  }

  const verificationResponse = await fetch("https://challenges.cloudflare.com/turnstile/v0/siteverify", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ secret, response: token, idempotency_key: idempotencyKey }),
  }).catch(() => null);
  if (!verificationResponse || !verificationResponse.ok) return false;

  const verification = await verificationResponse.json().catch(() => null);
  if (!isRecord(verification) || verification.success !== true) return false;

  return (!expectedHostname || verification.hostname === expectedHostname)
    && (!expectedAction || verification.action === expectedAction);
}

async function handleFeedback(request: Request, env: Env): Promise<Response> {
  const contentType = request.headers.get("content-type") ?? "";
  if (!/^application\/json(?:\s*;\s*charset=utf-8)?$/i.test(contentType)) {
    throw new HttpError(415, "content_type_required");
  }
  requireSameOrigin(request);

  const idempotencyKey = requireIdempotencyKey(request);
  await enforceSubmitRateLimit(request, env, idempotencyKey);
  const payload = parseFeedbackPayload(await readJson(request));
  const requestHash = await sha256(submissionFingerprint(payload));

  const existing = await env.DB.prepare(
    "SELECT id, request_hash FROM feedback WHERE idempotency_key = ? LIMIT 1",
  ).bind(idempotencyKey).first<ExistingSubmission>();
  if (existing) {
    if (existing.request_hash !== requestHash) throw new HttpError(409, "idempotency_conflict");
    return responseJson({ ok: true, id: existing.id, duplicate: true }, 200, { "cache-control": "no-store" });
  }

  if (!(await verifyTurnstile(env, payload.turnstileToken, idempotencyKey))) {
    throw new HttpError(403, "turnstile_failed");
  }

  const id = crypto.randomUUID();
  const result = await env.DB.prepare(
    `INSERT INTO feedback (
      id, idempotency_key, request_hash, type, message, contact,
      app_version, android_version, ui_locale, status
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'new')
    ON CONFLICT(idempotency_key) DO NOTHING`,
  ).bind(
    id,
    idempotencyKey,
    requestHash,
    payload.type,
    payload.message,
    payload.contact,
    payload.appVersion,
    payload.androidVersion,
    payload.uiLocale,
  ).run();

  if (result.meta.changes === 0) {
    const concurrent = await env.DB.prepare(
      "SELECT id, request_hash FROM feedback WHERE idempotency_key = ? LIMIT 1",
    ).bind(idempotencyKey).first<ExistingSubmission>();
    if (concurrent?.request_hash === requestHash) {
      return responseJson({ ok: true, id: concurrent.id, duplicate: true }, 200, { "cache-control": "no-store" });
    }
    throw new HttpError(409, "idempotency_conflict");
  }

  return responseJson({ ok: true, id, duplicate: false }, 201, { "cache-control": "no-store" });
}

async function handleBadge(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  const cacheKey = new Request(new URL("/badge.json", request.url).toString());
  const cache = getBadgeCache();
  const cached = await cache.match(cacheKey);
  if (cached) return cached;

  const countRow = await env.DB.prepare(
    "SELECT COUNT(*) AS count FROM feedback WHERE status = 'new'",
  ).first<{ count: number | string }>();
  const count = Number(countRow?.count ?? 0);
  const safeCount = Number.isSafeInteger(count) && count >= 0 ? count : 0;
  const color = safeCount === 0 ? "brightgreen" : safeCount < 5 ? "orange" : "red";
  const response = responseJson(
    {
      schemaVersion: 1,
      label: "feedback inbox",
      message: `${safeCount} awaiting review`,
      color,
      cacheSeconds: 300,
    },
    200,
    { "cache-control": "public, max-age=300, s-maxage=300, stale-while-revalidate=60" },
  );
  ctx.waitUntil(cache.put(cacheKey, response.clone()));
  return response;
}

async function handleHealth(env: Env): Promise<Response> {
  await env.DB.prepare("SELECT 1 AS ok").first<{ ok: number }>();
  return responseJson({ ok: true }, 200, { "cache-control": "no-store" });
}

async function deleteExpiredFeedback(env: Env): Promise<void> {
  await env.DB.prepare(
    "DELETE FROM feedback WHERE created_at < datetime('now', '-180 days')",
  ).run();
}

function withStaticSecurityHeaders(response: Response): Response {
  const headers = new Headers(response.headers);
  for (const [name, value] of Object.entries(STATIC_SECURITY_HEADERS)) headers.set(name, value);
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
}

const worker: ExportedHandler<Env> = {
  async fetch(request, env, ctx): Promise<Response> {
    const url = new URL(request.url);
    try {
      if (url.pathname === "/api/feedback") {
        if (request.method !== "POST") return errorResponse(new HttpError(405, "method_not_allowed"));
        return await handleFeedback(request, env);
      }
      if (url.pathname === "/api/config.json") {
        if (request.method !== "GET") return errorResponse(new HttpError(405, "method_not_allowed"));
        return responseJson(
          { turnstileSiteKey: env.TURNSTILE_SITE_KEY?.trim() || null },
          200,
          { "cache-control": "no-store" },
        );
      }
      if (url.pathname === "/badge.json") {
        if (request.method !== "GET") return errorResponse(new HttpError(405, "method_not_allowed"));
        return await handleBadge(request, env, ctx);
      }
      if (url.pathname === "/healthz") {
        if (request.method !== "GET") return errorResponse(new HttpError(405, "method_not_allowed"));
        return await handleHealth(env);
      }
      return withStaticSecurityHeaders(await env.ASSETS.fetch(request));
    } catch (error) {
      if (error instanceof HttpError) return errorResponse(error);
      return errorResponse(new HttpError(503, "service_unavailable"));
    }
  },
  async scheduled(_controller, env, ctx): Promise<void> {
    ctx.waitUntil(deleteExpiredFeedback(env));
  },
};

export default worker;
