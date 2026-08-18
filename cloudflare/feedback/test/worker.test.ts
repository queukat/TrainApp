import assert from "node:assert/strict";
import test from "node:test";
import worker from "../src/worker.js";

type TestEnv = {
  ASSETS: { fetch: (request: Request) => Promise<Response> };
  DB: MockDatabase;
  FEEDBACK_SUBMIT_RATE_LIMIT: { limit: (options: { key: string }) => Promise<{ success: boolean }> };
  ENVIRONMENT: string;
  TURNSTILE_SITE_KEY?: string;
  TURNSTILE_SECRET_KEY?: string;
  TURNSTILE_EXPECTED_HOSTNAME?: string;
  TURNSTILE_EXPECTED_ACTION?: string;
};

const fetchWorker = worker.fetch as unknown as (
  request: Request,
  env: TestEnv,
  ctx: ExecutionContext,
) => Promise<Response>;

interface StoredFeedback {
  id: string;
  idempotencyKey: string;
  requestHash: string;
  status: string;
}

class MockDatabase {
  readonly rows = new Map<string, StoredFeedback>();
  countQueries = 0;

  prepare(sql: string) {
    let values: unknown[] = [];
    const statement = {
      bind: (...nextValues: unknown[]) => {
        values = nextValues;
        return statement;
      },
      first: async <T>() => {
        if (sql.includes("COUNT(*)")) {
          this.countQueries += 1;
          const count = [...this.rows.values()].filter((row) => row.status === "new").length;
          return { count } as T;
        }
        if (sql.includes("SELECT 1 AS ok")) return { ok: 1 } as T;
        if (sql.includes("WHERE idempotency_key")) {
          const row = this.rows.get(values[0] as string);
          return row ? ({ id: row.id, request_hash: row.requestHash } as T) : null;
        }
        return null;
      },
      run: async () => {
        const idempotencyKey = values[1] as string;
        if (this.rows.has(idempotencyKey)) return { meta: { changes: 0 } };
        this.rows.set(idempotencyKey, {
          id: values[0] as string,
          idempotencyKey,
          requestHash: values[2] as string,
          status: "new",
        });
        return { meta: { changes: 1 } };
      },
    };
    return statement;
  }
}

const idempotencyKey = "dc8d3868-5375-4e90-a732-4a6271acbb55";

function environment(overrides: Partial<Record<string, unknown>> = {}) {
  const database = new MockDatabase();
  return {
    database,
    env: {
      ASSETS: { fetch: async () => new Response("asset") },
      DB: database,
      FEEDBACK_SUBMIT_RATE_LIMIT: { limit: async () => ({ success: true }) },
      ENVIRONMENT: "development",
      TURNSTILE_SITE_KEY: "site-key",
      ...overrides,
    } as TestEnv,
  };
}

function feedbackRequest(body: object, key = idempotencyKey) {
  return new Request("https://feedback.example/api/feedback", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "idempotency-key": key,
      origin: "https://feedback.example",
    },
    body: JSON.stringify(body),
  });
}

const validPayload = {
  type: "problem",
  message: "The timetable is unclear after a transfer.",
  contact: "",
  turnstileToken: "development-token",
  appVersion: "1.2.3",
  androidVersion: "14",
  uiLocale: "ru",
};

test("serves static assets after API routes", async () => {
  const { env } = environment();
  const response = await fetchWorker(new Request("https://feedback.example/"), env, {} as ExecutionContext);
  assert.equal(await response.text(), "asset");
});

test("returns only the public Turnstile site key in config", async () => {
  const { env } = environment();
  const response = await fetchWorker(new Request("https://feedback.example/api/config.json"), env, {} as ExecutionContext);
  assert.deepEqual(await response.json(), { turnstileSiteKey: "site-key" });
});

test("rejects malformed requests before persistence", async () => {
  const { database, env } = environment();
  const response = await fetchWorker(
    feedbackRequest({ ...validPayload, unexpected: true }),
    env,
    {} as ExecutionContext,
  );
  assert.equal(response.status, 400);
  assert.deepEqual(await response.json(), { ok: false, error: { code: "unknown_field" } });
  assert.equal(database.rows.size, 0);
});

test("rejects a cross-origin or non-JSON feedback request", async () => {
  const { env } = environment();
  const crossOrigin = feedbackRequest(validPayload);
  crossOrigin.headers.set("origin", "https://attacker.example");
  const crossOriginResponse = await fetchWorker(crossOrigin, env, {} as ExecutionContext);
  assert.equal(crossOriginResponse.status, 403);
  assert.deepEqual(await crossOriginResponse.json(), { ok: false, error: { code: "origin_not_allowed" } });

  const notJson = new Request("https://feedback.example/api/feedback", {
    method: "POST",
    headers: { origin: "https://feedback.example" },
    body: "not json",
  });
  const notJsonResponse = await fetchWorker(notJson, env, {} as ExecutionContext);
  assert.equal(notJsonResponse.status, 415);
  assert.deepEqual(await notJsonResponse.json(), { ok: false, error: { code: "content_type_required" } });
});

test("rejects an oversized JSON body before parsing", async () => {
  const { env } = environment();
  const response = await fetchWorker(
    new Request("https://feedback.example/api/feedback", {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "idempotency-key": idempotencyKey,
        origin: "https://feedback.example",
        "content-length": "8193",
      },
      body: "{}",
    }),
    env,
    {} as ExecutionContext,
  );
  assert.equal(response.status, 413);
  assert.deepEqual(await response.json(), { ok: false, error: { code: "payload_too_large" } });

  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      controller.enqueue(new TextEncoder().encode("x".repeat(8_193)));
      controller.close();
    },
  });
  const streamedResponse = await fetchWorker(
    new Request("https://feedback.example/api/feedback", {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "idempotency-key": idempotencyKey,
        origin: "https://feedback.example",
      },
      body: stream,
      // Node requires this for a streamed request; Workers ignores the field.
      duplex: "half",
    } as RequestInit),
    env,
    {} as ExecutionContext,
  );
  assert.equal(streamedResponse.status, 413);
});

test("rejects malformed JSON", async () => {
  const { env } = environment();
  const request = new Request("https://feedback.example/api/feedback", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "idempotency-key": idempotencyKey,
      origin: "https://feedback.example",
    },
    body: "{",
  });
  const response = await fetchWorker(request, env, {} as ExecutionContext);
  assert.equal(response.status, 400);
  assert.deepEqual(await response.json(), { ok: false, error: { code: "invalid_json" } });
});

test("stops rate-limited requests before Turnstile and D1", async () => {
  const { database, env } = environment({
    FEEDBACK_SUBMIT_RATE_LIMIT: { limit: async () => ({ success: false }) },
  });
  const response = await fetchWorker(feedbackRequest(validPayload), env, {} as ExecutionContext);
  assert.equal(response.status, 429);
  assert.equal(response.headers.get("retry-after"), "60");
  assert.deepEqual(await response.json(), { ok: false, error: { code: "rate_limited" } });
  assert.equal(database.rows.size, 0);
});

test("requires Turnstile configuration in production", async () => {
  const { database, env } = environment({ ENVIRONMENT: "production" });
  const response = await fetchWorker(feedbackRequest(validPayload), env, {} as ExecutionContext);
  assert.equal(response.status, 503);
  assert.deepEqual(await response.json(), { ok: false, error: { code: "turnstile_not_configured" } });
  assert.equal(database.rows.size, 0);
});

test("fails closed for an unsuccessful or mismatched production Turnstile response", async (t) => {
  const originalFetch = globalThis.fetch;
  t.after(() => {
    globalThis.fetch = originalFetch;
  });
  const { env } = environment({
    ENVIRONMENT: "production",
    TURNSTILE_SECRET_KEY: "secret",
    TURNSTILE_EXPECTED_HOSTNAME: "feedback.example",
    TURNSTILE_EXPECTED_ACTION: "feedback",
  });

  globalThis.fetch = async () => Response.json({ success: false });
  const failed = await fetchWorker(feedbackRequest(validPayload), env, {} as ExecutionContext);
  assert.equal(failed.status, 403);

  globalThis.fetch = async () => Response.json({ success: true, hostname: "other.example", action: "feedback" });
  const wrongHostname = await fetchWorker(feedbackRequest(validPayload), env, {} as ExecutionContext);
  assert.equal(wrongHostname.status, 403);

  globalThis.fetch = async () => Response.json({ success: true, hostname: "feedback.example", action: "other" });
  const wrongAction = await fetchWorker(feedbackRequest(validPayload), env, {} as ExecutionContext);
  assert.equal(wrongAction.status, 403);
});

test("persists a valid submission once and accepts an identical retry", async () => {
  const { database, env } = environment();
  const first = await fetchWorker(feedbackRequest(validPayload), env, {} as ExecutionContext);
  assert.equal(first.status, 201);
  const firstBody = await first.json() as { id: string; duplicate: boolean };
  assert.equal(firstBody.duplicate, false);
  assert.equal(database.rows.size, 1);

  const retry = await fetchWorker(feedbackRequest(validPayload), env, {} as ExecutionContext);
  assert.equal(retry.status, 200);
  assert.deepEqual(await retry.json(), { ok: true, id: firstBody.id, duplicate: true });
  assert.equal(database.rows.size, 1);
});

test("rejects a conflicting reuse of an idempotency key", async () => {
  const { env } = environment();
  await fetchWorker(feedbackRequest(validPayload), env, {} as ExecutionContext);
  const response = await fetchWorker(
    feedbackRequest({ ...validPayload, message: "A different report with the same key." }),
    env,
    {} as ExecutionContext,
  );
  assert.equal(response.status, 409);
  assert.deepEqual(await response.json(), { ok: false, error: { code: "idempotency_conflict" } });
});

test("returns a cached aggregate new-feedback badge without another D1 count", async (t) => {
  const { env } = environment();
  const originalCaches = globalThis.caches;
  const entries = new Map<string, Response>();
  Object.defineProperty(globalThis, "caches", {
    configurable: true,
    value: {
      default: {
        match: async (request: Request) => entries.get(request.url)?.clone(),
        put: async (request: Request, response: Response) => {
          entries.set(request.url, response.clone());
        },
      },
    },
  });
  t.after(() => {
    Object.defineProperty(globalThis, "caches", { configurable: true, value: originalCaches });
  });
  const pending: Promise<unknown>[] = [];
  const context = { waitUntil: (promise: Promise<unknown>) => pending.push(promise) } as unknown as ExecutionContext;
  const response = await fetchWorker(new Request("https://feedback.example/badge.json"), env, context);
  assert.deepEqual(await response.json(), {
    schemaVersion: 1,
    label: "feedback inbox",
    message: "0 awaiting review",
    color: "brightgreen",
    cacheSeconds: 300,
  });
  assert.match(response.headers.get("cache-control") ?? "", /max-age=300/);
  await Promise.all(pending);
  const cachedResponse = await fetchWorker(new Request("https://feedback.example/badge.json"), env, context);
  assert.equal(cachedResponse.status, 200);
  assert.equal((env.DB as unknown as MockDatabase).countQueries, 1);
});

test("adds strict security headers to static assets", async () => {
  const { env } = environment();
  const response = await fetchWorker(new Request("https://feedback.example/"), env, {} as ExecutionContext);
  const csp = response.headers.get("content-security-policy") ?? "";
  assert.match(csp, /script-src 'self' https:\/\/challenges\.cloudflare\.com/);
  assert.match(csp, /frame-ancestors 'none'/);
  assert.equal(response.headers.get("x-content-type-options"), "nosniff");
});

test("checks D1 for health", async () => {
  const { env } = environment();
  const response = await fetchWorker(new Request("https://feedback.example/healthz"), env, {} as ExecutionContext);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { ok: true });
});
