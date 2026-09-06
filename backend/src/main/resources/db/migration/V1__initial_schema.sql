CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email text NOT NULL UNIQUE,
  password_hash text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE dataset_batches (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES users(id),
  kind text NOT NULL CHECK (kind IN ('orders', 'payments')),
  file_name text NOT NULL,
  row_count integer NOT NULL,
  warnings jsonb NOT NULL DEFAULT '[]'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE orders (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES users(id),
  batch_id uuid NOT NULL REFERENCES dataset_batches(id),
  order_id text NOT NULL,
  order_id_norm text NOT NULL,
  order_date timestamptz,
  customer_email text,
  currency text NOT NULL,
  gross_amount numeric(12,2) NOT NULL,
  discount numeric(12,2) NOT NULL DEFAULT 0,
  net_amount numeric(12,2) NOT NULL,
  status text NOT NULL
);
CREATE INDEX orders_user_order_idx ON orders(user_id, order_id_norm);

CREATE TABLE payments (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES users(id),
  batch_id uuid NOT NULL REFERENCES dataset_batches(id),
  transaction_ref text NOT NULL,
  order_reference text,
  order_ref_norm text,
  processed_at timestamptz,
  currency text NOT NULL,
  amount numeric(12,2) NOT NULL,
  fee numeric(12,2) NOT NULL DEFAULT 0,
  net_settled numeric(12,2),
  type text NOT NULL CHECK (type IN ('charge', 'refund')),
  status text NOT NULL CHECK (status IN ('settled', 'pending', 'failed'))
);
CREATE INDEX payments_user_order_idx ON payments(user_id, order_ref_norm);

CREATE TABLE reconciliation_runs (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES users(id),
  ran_at timestamptz NOT NULL DEFAULT now(),
  orders_batch_id uuid NOT NULL REFERENCES dataset_batches(id),
  payments_batch_id uuid NOT NULL REFERENCES dataset_batches(id),
  summary jsonb NOT NULL
);

CREATE TABLE discrepancies (
  id uuid PRIMARY KEY,
  run_id uuid NOT NULL REFERENCES reconciliation_runs(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id),
  order_id uuid REFERENCES orders(id),
  payment_id uuid REFERENCES payments(id),
  type text NOT NULL,
  severity text NOT NULL CHECK (severity IN ('critical', 'high', 'ingestion')),
  amount_at_risk numeric(12,2) NOT NULL DEFAULT 0,
  details jsonb NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX discrepancies_run_idx ON discrepancies(user_id, run_id, type, severity);

CREATE TABLE discrepancy_explanations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  discrepancy_id uuid NOT NULL UNIQUE REFERENCES discrepancies(id) ON DELETE CASCADE,
  model text NOT NULL,
  temperature numeric(3,2) NOT NULL,
  likely_cause text NOT NULL,
  recommended_action text NOT NULL,
  confidence text NOT NULL,
  raw_response jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);