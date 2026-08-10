ALTER TABLE ops.processed_event
    ADD COLUMN IF NOT EXISTS source_system varchar(128),
    ADD COLUMN IF NOT EXISTS partition_key varchar(256),
    ADD COLUMN IF NOT EXISTS payload_sha256 char(64);

CREATE TABLE IF NOT EXISTS ops.raw_event (
    event_id uuid PRIMARY KEY,
    schema_version varchar(16) NOT NULL,
    event_type varchar(64) NOT NULL,
    source_system varchar(128) NOT NULL,
    source_timestamp timestamptz NOT NULL,
    published_at timestamptz NOT NULL,
    partition_key varchar(256) NOT NULL,
    source_sequence bigint,
    source_hash char(64) NOT NULL,
    quality_flags text[] NOT NULL DEFAULT '{}',
    payload jsonb NOT NULL,
    received_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS raw_event_source_partition_idx
    ON ops.raw_event(source_system, partition_key, source_timestamp DESC);

CREATE TABLE IF NOT EXISTS ai.agent_step (
    run_id uuid NOT NULL REFERENCES ai.agent_run(run_id),
    sequence integer NOT NULL,
    state varchar(32) NOT NULL,
    detail text NOT NULL,
    occurred_at timestamptz NOT NULL,
    PRIMARY KEY (run_id, sequence)
);

CREATE TABLE IF NOT EXISTS ops.evidence_ref (
    evidence_id varchar(128) PRIMARY KEY,
    evidence_type varchar(64) NOT NULL,
    evidence_version varchar(64) NOT NULL,
    sha256 char(64) NOT NULL,
    observed_at timestamptz NOT NULL,
    payload jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS ops.incident (
    incident_id uuid PRIMARY KEY,
    account_id varchar(64),
    severity varchar(16) NOT NULL CHECK (severity IN ('INFO','WARNING','CRITICAL')),
    status varchar(16) NOT NULL CHECK (status IN ('OPEN','ACKED','CLOSED')),
    title varchar(256) NOT NULL,
    source_event_id uuid,
    evidence_id varchar(128),
    created_by varchar(128) NOT NULL,
    acknowledged_by varchar(128),
    closed_by varchar(128),
    created_at timestamptz NOT NULL DEFAULT now(),
    acknowledged_at timestamptz,
    closed_at timestamptz,
    close_reason text
);

CREATE INDEX IF NOT EXISTS incident_account_status_idx
    ON ops.incident(account_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS ops.action_item (
    action_item_id uuid PRIMARY KEY,
    incident_id uuid NOT NULL REFERENCES ops.incident(incident_id),
    owner varchar(128) NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('OPEN','DONE','CANCELLED')),
    description text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz
);

CREATE INDEX IF NOT EXISTS agent_run_updated_idx
    ON ai.agent_run(updated_at DESC);

CREATE INDEX IF NOT EXISTS report_created_idx
    ON ai.report(created_at DESC);

CREATE INDEX IF NOT EXISTS processed_event_partition_idx
    ON ops.processed_event(partition_key, source_timestamp DESC);

ALTER TABLE ops.incident ENABLE ROW LEVEL SECURITY;
ALTER TABLE ops.incident FORCE ROW LEVEL SECURITY;

CREATE POLICY incident_account_policy ON ops.incident
    USING (account_id IS NULL OR account_id::text = ANY (string_to_array(current_setting('app.allowed_accounts', true), ',')))
    WITH CHECK (account_id IS NULL OR account_id::text = ANY (string_to_array(current_setting('app.allowed_accounts', true), ',')));
