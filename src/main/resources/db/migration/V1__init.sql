CREATE TABLE experiments (
  key             TEXT PRIMARY KEY,
  description     TEXT NOT NULL,
  salt            TEXT NOT NULL,
  status          TEXT NOT NULL CHECK (status IN ('DRAFT', 'RUNNING', 'STOPPED')),
  variants        JSONB NOT NULL,
  traffic_percent INT NOT NULL CHECK (traffic_percent BETWEEN 1 AND 100),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE exposures (
  experiment_key TEXT NOT NULL REFERENCES experiments(key),
  variant        TEXT NOT NULL,
  user_id        TEXT NOT NULL,
  exposed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (experiment_key, user_id)
);

CREATE TABLE conversions (
  experiment_key TEXT NOT NULL,
  variant        TEXT NOT NULL,
  user_id        TEXT NOT NULL,
  metric         TEXT NOT NULL,
  converted_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (experiment_key, user_id, metric),
  FOREIGN KEY (experiment_key, user_id) REFERENCES exposures(experiment_key, user_id)
);
