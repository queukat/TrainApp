CREATE TABLE feedback (
  id TEXT PRIMARY KEY NOT NULL,
  idempotency_key TEXT NOT NULL UNIQUE,
  request_hash TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  type TEXT NOT NULL CHECK (type IN ('problem', 'idea', 'other')),
  message TEXT NOT NULL,
  contact TEXT,
  app_version TEXT,
  android_version TEXT,
  ui_locale TEXT,
  status TEXT NOT NULL DEFAULT 'new' CHECK (status IN ('new', 'reviewed', 'closed'))
);

CREATE INDEX feedback_status_created_at_idx
  ON feedback (status, created_at DESC);

CREATE INDEX feedback_created_at_idx
  ON feedback (created_at);
