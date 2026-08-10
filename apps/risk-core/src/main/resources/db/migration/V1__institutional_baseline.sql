CREATE SCHEMA IF NOT EXISTS reference;
CREATE SCHEMA IF NOT EXISTS trading;
CREATE SCHEMA IF NOT EXISTS risk;
CREATE SCHEMA IF NOT EXISTS ops;
CREATE SCHEMA IF NOT EXISTS ai;
CREATE SCHEMA IF NOT EXISTS audit;

CREATE TABLE reference.desk (
    desk_id varchar(64) PRIMARY KEY,
    name varchar(128) NOT NULL,
    institution_id varchar(64) NOT NULL
);
CREATE TABLE reference.portfolio (
    portfolio_id varchar(64) PRIMARY KEY,
    desk_id varchar(64) NOT NULL REFERENCES reference.desk,
    name varchar(128) NOT NULL
);
CREATE TABLE reference.account (
    account_id varchar(64) PRIMARY KEY,
    portfolio_id varchar(64) NOT NULL REFERENCES reference.portfolio,
    currency char(3) NOT NULL,
    net_asset_value decimal(38,10) NOT NULL CHECK (net_asset_value >= 0),
    margin_capacity decimal(38,10) NOT NULL CHECK (margin_capacity >= 0)
);
CREATE TABLE reference.instrument (
    instrument_id varchar(64) PRIMARY KEY,
    symbol varchar(32) NOT NULL,
    venue varchar(16) NOT NULL,
    asset_class varchar(32) NOT NULL CHECK (asset_class IN ('EQUITY','INDEX_FUTURE')),
    currency char(3) NOT NULL,
    contract_multiplier decimal(38,10),
    margin_rate decimal(38,10)
);

CREATE TABLE trading.market_snapshot (
    instrument_id varchar(64) NOT NULL REFERENCES reference.instrument,
    observed_at timestamptz NOT NULL,
    open_price decimal(38,10) NOT NULL,
    high_price decimal(38,10) NOT NULL,
    low_price decimal(38,10) NOT NULL,
    close_price decimal(38,10) NOT NULL,
    previous_close decimal(38,10) NOT NULL,
    bid_price decimal(38,10),
    ask_price decimal(38,10),
    volume decimal(38,10) NOT NULL,
    data_version varchar(64) NOT NULL,
    quality_flags jsonb NOT NULL DEFAULT '[]',
    PRIMARY KEY (instrument_id, observed_at)
);
CREATE TABLE trading.position_snapshot (
    account_id varchar(64) NOT NULL REFERENCES reference.account,
    instrument_id varchar(64) NOT NULL REFERENCES reference.instrument,
    observed_at timestamptz NOT NULL,
    side varchar(8) NOT NULL CHECK (side IN ('LONG','SHORT')),
    quantity decimal(38,10) NOT NULL,
    average_price decimal(38,10) NOT NULL,
    current_price decimal(38,10) NOT NULL,
    source_event_id uuid NOT NULL,
    PRIMARY KEY (account_id, instrument_id, observed_at)
);
CREATE TABLE trading.oms_order (
    order_id varchar(64) PRIMARY KEY,
    account_id varchar(64) NOT NULL REFERENCES reference.account,
    trade_date date NOT NULL,
    instrument_id varchar(64) NOT NULL REFERENCES reference.instrument,
    quantity decimal(38,10) NOT NULL,
    price decimal(38,10) NOT NULL,
    status varchar(32) NOT NULL,
    currency char(3) NOT NULL,
    source_event_id uuid NOT NULL
);
CREATE TABLE trading.broker_execution (
    row_id uuid PRIMARY KEY,
    execution_id varchar(64) NOT NULL,
    order_id varchar(64),
    account_id varchar(64) NOT NULL REFERENCES reference.account,
    trade_date date NOT NULL,
    quantity decimal(38,10) NOT NULL,
    price decimal(38,10) NOT NULL,
    status varchar(32) NOT NULL,
    currency char(3) NOT NULL,
    source_event_id uuid NOT NULL
);

CREATE TABLE risk.risk_limit (
    limit_id uuid PRIMARY KEY,
    account_id varchar(64) NOT NULL REFERENCES reference.account,
    limit_code varchar(64) NOT NULL CHECK (limit_code IN ('GROSS_EXPOSURE','NET_EXPOSURE','SINGLE_INSTRUMENT_CONCENTRATION','LEVERAGE','MARGIN_UTILIZATION','MARKET_FRESHNESS_SECONDS')),
    limit_value decimal(38,10) NOT NULL,
    severity varchar(16) NOT NULL,
    valid_from timestamptz NOT NULL,
    valid_to timestamptz,
    UNIQUE (account_id, limit_code, valid_from)
);
CREATE TABLE risk.risk_breach (
    breach_id uuid PRIMARY KEY,
    account_id varchar(64) NOT NULL REFERENCES reference.account,
    limit_code varchar(64) NOT NULL,
    actual_value decimal(38,10) NOT NULL,
    limit_value decimal(38,10) NOT NULL,
    observed_at timestamptz NOT NULL,
    evidence_id varchar(128) NOT NULL
);

CREATE TABLE ops.processed_event (
    event_id uuid PRIMARY KEY,
    event_type varchar(64) NOT NULL,
    schema_version varchar(16) NOT NULL,
    source_timestamp timestamptz NOT NULL,
    processed_at timestamptz NOT NULL DEFAULT now(),
    disposition varchar(32) NOT NULL CHECK (disposition IN ('APPLIED','DUPLICATE','OUT_OF_ORDER','FUTURE_REJECTED','STALE_APPLIED'))
);
CREATE TABLE ops.transactional_outbox (
    outbox_id uuid PRIMARY KEY,
    aggregate_type varchar(64) NOT NULL,
    aggregate_id varchar(128) NOT NULL,
    topic varchar(64) NOT NULL,
    event_id uuid NOT NULL UNIQUE,
    schema_version varchar(16) NOT NULL,
    payload jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    published_at timestamptz,
    attempts integer NOT NULL DEFAULT 0
);

CREATE TABLE ai.agent_run (
    run_id uuid PRIMARY KEY,
    idempotency_key varchar(128) NOT NULL UNIQUE,
    account_id varchar(64) NOT NULL REFERENCES reference.account,
    trade_date date NOT NULL,
    state varchar(32) NOT NULL,
    step_count integer NOT NULL DEFAULT 0,
    model_calls integer NOT NULL DEFAULT 0,
    estimated_cost_usd decimal(38,10) NOT NULL DEFAULT 0,
    prompt_version varchar(64),
    model_version varchar(128),
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE ai.report (
    report_id uuid PRIMARY KEY,
    run_id uuid NOT NULL REFERENCES ai.agent_run,
    account_id varchar(64) NOT NULL REFERENCES reference.account,
    trade_date date NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('DRAFT','APPROVED','REJECTED')),
    creator varchar(128) NOT NULL,
    decided_by varchar(128),
    decision_reason text,
    version integer NOT NULL DEFAULT 1,
    json_object_uri text,
    html_object_uri text,
    content_sha256 char(64),
    created_at timestamptz NOT NULL DEFAULT now(),
    decided_at timestamptz,
    CHECK (status <> 'APPROVED' OR (decided_by IS NOT NULL AND decided_by <> creator AND content_sha256 IS NOT NULL))
);

CREATE TABLE audit.audit_event (
    sequence_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id uuid NOT NULL UNIQUE,
    occurred_at timestamptz NOT NULL,
    subject_id varchar(256) NOT NULL,
    client_id varchar(128) NOT NULL,
    scopes text[] NOT NULL,
    authorization_decision varchar(32) NOT NULL,
    desk_id varchar(64),
    account_id varchar(64),
    tool_name varchar(64),
    agent_step varchar(64),
    model_version varchar(128),
    prompt_sha256 char(64),
    input_sha256 char(64) NOT NULL,
    output_sha256 char(64) NOT NULL,
    evidence_ids text[] NOT NULL DEFAULT '{}',
    token_count integer,
    estimated_cost_usd decimal(38,10),
    duration_ms bigint NOT NULL,
    outcome varchar(32) NOT NULL,
    error_code varchar(64),
    trace_id char(32) NOT NULL,
    previous_hash char(64) NOT NULL,
    event_hash char(64) NOT NULL UNIQUE
);

CREATE OR REPLACE FUNCTION audit.reject_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit events are append-only';
END $$;
CREATE TRIGGER audit_event_no_update_delete BEFORE UPDATE OR DELETE ON audit.audit_event
FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();

ALTER TABLE trading.position_snapshot ENABLE ROW LEVEL SECURITY;
ALTER TABLE trading.oms_order ENABLE ROW LEVEL SECURITY;
ALTER TABLE trading.broker_execution ENABLE ROW LEVEL SECURITY;
ALTER TABLE risk.risk_breach ENABLE ROW LEVEL SECURITY;
ALTER TABLE ai.agent_run ENABLE ROW LEVEL SECURITY;
ALTER TABLE ai.report ENABLE ROW LEVEL SECURITY;

ALTER TABLE trading.position_snapshot FORCE ROW LEVEL SECURITY;
ALTER TABLE trading.oms_order FORCE ROW LEVEL SECURITY;
ALTER TABLE trading.broker_execution FORCE ROW LEVEL SECURITY;
ALTER TABLE risk.risk_breach FORCE ROW LEVEL SECURITY;
ALTER TABLE ai.agent_run FORCE ROW LEVEL SECURITY;
ALTER TABLE ai.report FORCE ROW LEVEL SECURITY;

CREATE POLICY position_account_policy ON trading.position_snapshot USING (account_id::text = ANY (string_to_array(current_setting('app.allowed_accounts', true), ',')));
CREATE POLICY order_account_policy ON trading.oms_order USING (account_id::text = ANY (string_to_array(current_setting('app.allowed_accounts', true), ',')));
CREATE POLICY execution_account_policy ON trading.broker_execution USING (account_id::text = ANY (string_to_array(current_setting('app.allowed_accounts', true), ',')));
CREATE POLICY breach_account_policy ON risk.risk_breach USING (account_id::text = ANY (string_to_array(current_setting('app.allowed_accounts', true), ',')));
CREATE POLICY agent_account_policy ON ai.agent_run USING (account_id::text = ANY (string_to_array(current_setting('app.allowed_accounts', true), ',')));
CREATE POLICY report_account_policy ON ai.report USING (account_id::text = ANY (string_to_array(current_setting('app.allowed_accounts', true), ',')));

-- Migration-only seed scope; runtime requests always bind one already-authorized account transaction-locally.
SELECT set_config('app.allowed_accounts', 'ACC_ALPHA_01,ACC_ALPHA_02,ACC_BETA_01,ACC_BETA_02', false);

INSERT INTO reference.desk VALUES ('DESK_ALPHA','Alpha Equity and Index Desk','SYNTHETIC_INSTITUTION'),('DESK_BETA','Beta Trading Operations','SYNTHETIC_INSTITUTION');
INSERT INTO reference.portfolio VALUES ('PORT_ALPHA','DESK_ALPHA','Alpha Portfolio'),('PORT_BETA','DESK_BETA','Beta Portfolio');
INSERT INTO reference.account VALUES
('ACC_ALPHA_01','PORT_ALPHA','CNY',70000000,20000000),('ACC_ALPHA_02','PORT_ALPHA','CNY',50000000,15000000),
('ACC_BETA_01','PORT_BETA','CNY',80000000,25000000),('ACC_BETA_02','PORT_BETA','CNY',60000000,18000000);
INSERT INTO reference.instrument VALUES
('SSE:600519','600519','SSE','EQUITY','CNY',NULL,NULL),('SSE:601318','601318','SSE','EQUITY','CNY',NULL,NULL),
('SSE:600036','600036','SSE','EQUITY','CNY',NULL,NULL),('SZSE:000001','000001','SZSE','EQUITY','CNY',NULL,NULL),
('SZSE:000858','000858','SZSE','EQUITY','CNY',NULL,NULL),('SZSE:300750','300750','SZSE','EQUITY','CNY',NULL,NULL),
('CFFEX:IF2608','IF2608','CFFEX','INDEX_FUTURE','CNY',300,0.12),('CFFEX:IC2608','IC2608','CFFEX','INDEX_FUTURE','CNY',200,0.14);

INSERT INTO trading.market_snapshot(instrument_id,observed_at,open_price,high_price,low_price,close_price,previous_close,bid_price,ask_price,volume,data_version,quality_flags) VALUES
('SSE:600519','2026-08-07T07:00:00Z',1412,1438.5,1408,1431.25,1409.8,1431.20,1431.30,2865400,'2026-08-07.v1','[]'),
('SSE:601318','2026-08-07T07:00:00Z',58.1,59.2,57.9,58.88,58,58.87,58.89,58000000,'2026-08-07.v1','[]'),
('SSE:600036','2026-08-07T07:00:00Z',45.2,46.1,45,45.86,45.1,45.85,45.87,42000000,'2026-08-07.v1','[]'),
('SZSE:000001','2026-08-07T07:00:00Z',11.42,11.68,11.35,11.58,11.41,11.57,11.59,93000000,'2026-08-07.v1','[]'),
('SZSE:000858','2026-08-07T07:00:00Z',138,140.2,137.5,139.3,137.9,139.29,139.31,9100000,'2026-08-07.v1','[]'),
('SZSE:300750','2026-08-07T07:00:00Z',228,234,226.5,232.6,227.8,232.5,232.7,26500000,'2026-08-07.v1','["STALE_TEST_CASE"]'),
('CFFEX:IF2608','2026-08-07T07:00:00Z',3978,4018.8,3990.6,4002.8,4010,4002.6,4003.0,82210,'2026-08-07.v1','[]'),
('CFFEX:IC2608','2026-08-07T07:00:00Z',6201.4,6250.6,6188.2,6238.2,6201.4,6238.0,6238.4,61120,'2026-08-07.v1','[]');

INSERT INTO trading.position_snapshot(account_id,instrument_id,observed_at,side,quantity,average_price,current_price,source_event_id) VALUES
('ACC_ALPHA_01','SSE:600519','2026-08-07T07:00:00Z','LONG',30000,1400,1431.25,'10000000-0000-0000-0000-000000000001'),
('ACC_ALPHA_01','SSE:601318','2026-08-07T07:00:00Z','LONG',200000,55,58.88,'10000000-0000-0000-0000-000000000002'),
('ACC_ALPHA_01','CFFEX:IF2608','2026-08-07T07:00:00Z','SHORT',100,4010,4002.8,'10000000-0000-0000-0000-000000000003'),
('ACC_ALPHA_02','SSE:600036','2026-08-07T07:00:00Z','LONG',100000,42,45.86,'10000000-0000-0000-0000-000000000004'),
('ACC_BETA_01','SZSE:000858','2026-08-07T07:00:00Z','LONG',80000,135,139.3,'10000000-0000-0000-0000-000000000005'),
('ACC_BETA_02','CFFEX:IC2608','2026-08-07T07:00:00Z','LONG',50,6200,6238.2,'10000000-0000-0000-0000-000000000006');

INSERT INTO trading.oms_order(order_id,account_id,trade_date,instrument_id,quantity,price,status,currency,source_event_id) VALUES
('OMS-A-1001','ACC_ALPHA_01','2026-08-07','SSE:601318',1000,10,'FILLED','CNY','20000000-0000-0000-0000-000000000001'),
('OMS-A-1002','ACC_ALPHA_01','2026-08-07','SSE:600036',500,20,'FILLED','CNY','20000000-0000-0000-0000-000000000002');
INSERT INTO trading.broker_execution(row_id,execution_id,order_id,account_id,trade_date,quantity,price,status,currency,source_event_id) VALUES
('30000000-0000-0000-0000-000000000001','BRK-E-1','OMS-A-1001','ACC_ALPHA_01','2026-08-07',500,10.05,'FILLED','CNY','40000000-0000-0000-0000-000000000001'),
('30000000-0000-0000-0000-000000000002','BRK-E-2','OMS-A-1001','ACC_ALPHA_01','2026-08-07',400,10.05,'FILLED','USD','40000000-0000-0000-0000-000000000002'),
('30000000-0000-0000-0000-000000000003','BRK-E-2','OMS-A-1001','ACC_ALPHA_01','2026-08-07',400,10.05,'FILLED','USD','40000000-0000-0000-0000-000000000003'),
('30000000-0000-0000-0000-000000000004','BRK-E-9','OMS-UNKNOWN','ACC_ALPHA_01','2026-08-07',200,30,'FILLED','CNY','40000000-0000-0000-0000-000000000004');

INSERT INTO risk.risk_limit(limit_id,account_id,limit_code,limit_value,severity,valid_from) VALUES
('50000000-0000-0000-0000-000000000001','ACC_ALPHA_01','GROSS_EXPOSURE',180000000,'CRITICAL','2026-01-01T00:00:00Z'),
('50000000-0000-0000-0000-000000000002','ACC_ALPHA_01','NET_EXPOSURE',80000000,'CRITICAL','2026-01-01T00:00:00Z'),
('50000000-0000-0000-0000-000000000003','ACC_ALPHA_01','SINGLE_INSTRUMENT_CONCENTRATION',0.4,'CRITICAL','2026-01-01T00:00:00Z'),
('50000000-0000-0000-0000-000000000004','ACC_ALPHA_01','LEVERAGE',2.5,'WARNING','2026-01-01T00:00:00Z'),
('50000000-0000-0000-0000-000000000005','ACC_ALPHA_01','MARGIN_UTILIZATION',0.7,'CRITICAL','2026-01-01T00:00:00Z'),
('50000000-0000-0000-0000-000000000006','ACC_ALPHA_01','MARKET_FRESHNESS_SECONDS',60,'WARNING','2026-01-01T00:00:00Z');

SELECT set_config('app.allowed_accounts', '', false);

-- Runtime application role is deliberately denied mutation of audit history.
REVOKE UPDATE, DELETE, TRUNCATE ON audit.audit_event FROM PUBLIC;
