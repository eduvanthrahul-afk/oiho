CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE TABLE companies (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(160) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
  name VARCHAR(120) NOT NULL,
  email VARCHAR(254) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'OWNER',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX users_company_idx ON users(company_id);
CREATE TABLE refresh_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX refresh_tokens_user_idx ON refresh_tokens(user_id);
CREATE TABLE decisions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
  created_by UUID NOT NULL REFERENCES users(id),
  title VARCHAR(160) NOT NULL,
  product_description TEXT NOT NULL DEFAULT '',
  target_market VARCHAR(240) NOT NULL DEFAULT '',
  target_customer VARCHAR(240) NOT NULL DEFAULT '',
  location VARCHAR(160) NOT NULL DEFAULT '',
  market_size INTEGER NOT NULL,
  current_price NUMERIC(12,2) NOT NULL DEFAULT 0,
  customer_count INTEGER NOT NULL DEFAULT 300,
  scenarios_json TEXT NOT NULL,
  latest_simulation_id UUID,
  recommendation VARCHAR(40),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX decisions_company_created_idx ON decisions(company_id, created_at DESC);
CREATE TABLE simulations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
  decision_id UUID NOT NULL REFERENCES decisions(id) ON DELETE CASCADE,
  status VARCHAR(20) NOT NULL,
  progress INTEGER NOT NULL DEFAULT 0,
  request_json TEXT NOT NULL,
  result_json TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ
);
CREATE INDEX simulations_company_idx ON simulations(company_id, created_at DESC);
