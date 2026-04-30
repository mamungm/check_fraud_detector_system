CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE institution (
  institution_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  region TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE account_token (
  account_token TEXT PRIMARY KEY,
  institution_id UUID REFERENCES institution(institution_id),
  account_type TEXT,
  opened_at TIMESTAMPTZ,
  risk_tier TEXT DEFAULT 'LOW'
);

CREATE TABLE payee_token (
  payee_token TEXT PRIMARY KEY,
  first_seen_at TIMESTAMPTZ DEFAULT now(),
  last_seen_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE device_token (
  device_token TEXT PRIMARY KEY,
  first_seen_at TIMESTAMPTZ DEFAULT now(),
  last_seen_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE check_item (
  check_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  check_serial TEXT,
  micr_routing_hash TEXT,
  micr_account_hash TEXT,
  image_fingerprint TEXT,
  amount_text TEXT,
  legal_amount NUMERIC(12,2),
  courtesy_amount NUMERIC(12,2),
  payor_token TEXT,
  payee_token TEXT,
  endorsement_present BOOLEAN,
  signature_present BOOLEAN,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE deposit_event (
  event_id UUID PRIMARY KEY,
  institution_id UUID REFERENCES institution(institution_id),
  channel TEXT CHECK (channel IN ('mobile','ATM','branch')),
  deposit_timestamp TIMESTAMPTZ NOT NULL,
  amount NUMERIC(12,2) NOT NULL,
  currency TEXT DEFAULT 'CAD',
  account_token TEXT,
  payee_token TEXT,
  device_token TEXT,
  region TEXT,
  check_serial TEXT,
  micr_routing_hash TEXT,
  micr_account_hash TEXT,
  image_front_uri TEXT,
  image_back_uri TEXT,
  status TEXT DEFAULT 'RECEIVED',
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE clearing_event (
  clearing_event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  deposit_event_id UUID REFERENCES deposit_event(event_id),
  presentment_timestamp TIMESTAMPTZ NOT NULL,
  amount NUMERIC(12,2) NOT NULL,
  drawee_routing_hash TEXT,
  return_code TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE consortium_signal (
  signal_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  event_id UUID,
  token_type TEXT NOT NULL,
  token_value_hash TEXT NOT NULL,
  institution_count INT DEFAULT 0,
  fraud_count_7d INT DEFAULT 0,
  fraud_count_30d INT DEFAULT 0,
  duplicate_appearance_count INT DEFAULT 0,
  network_risk_score NUMERIC(5,4) DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE model_score (
  score_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  event_id UUID REFERENCES deposit_event(event_id),
  model_name TEXT NOT NULL,
  model_version TEXT NOT NULL,
  score NUMERIC(5,4) NOT NULL,
  explanation JSONB,
  latency_ms INT,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE rule_hit (
  rule_hit_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  event_id UUID REFERENCES deposit_event(event_id),
  rule_code TEXT NOT NULL,
  severity TEXT CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
  evidence JSONB,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE fraud_alert (
  alert_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  event_id UUID REFERENCES deposit_event(event_id),
  risk_score NUMERIC(5,4) NOT NULL,
  decision TEXT CHECK (decision IN ('PASS','MANUAL_REVIEW','HOLD')),
  reason_codes TEXT[],
  explanation JSONB,
  status TEXT DEFAULT 'OPEN',
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE fraud_case (
  case_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  alert_id UUID REFERENCES fraud_alert(alert_id),
  assigned_to TEXT,
  status TEXT DEFAULT 'NEW',
  priority TEXT DEFAULT 'MEDIUM',
  created_at TIMESTAMPTZ DEFAULT now(),
  closed_at TIMESTAMPTZ
);

CREATE TABLE investigator_action (
  action_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  case_id UUID REFERENCES fraud_case(case_id),
  actor TEXT NOT NULL,
  action_type TEXT NOT NULL,
  notes TEXT,
  disposition TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE audit_log (
  audit_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  actor TEXT,
  action TEXT NOT NULL,
  resource_type TEXT,
  resource_id TEXT,
  metadata JSONB,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_deposit_event_account_time ON deposit_event(account_token, deposit_timestamp);
CREATE INDEX idx_deposit_event_payee_time ON deposit_event(payee_token, deposit_timestamp);
CREATE INDEX idx_deposit_event_micr ON deposit_event(micr_routing_hash, micr_account_hash, check_serial);
CREATE INDEX idx_consortium_token ON consortium_signal(token_type, token_value_hash);
