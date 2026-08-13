-- A diagnosis run represents one governed reporting workflow.  The unique index
-- makes POST /reports safely idempotent even when requests reach different pods.
CREATE UNIQUE INDEX IF NOT EXISTS report_diagnosis_run_unique_idx
    ON ai.report(run_id);
