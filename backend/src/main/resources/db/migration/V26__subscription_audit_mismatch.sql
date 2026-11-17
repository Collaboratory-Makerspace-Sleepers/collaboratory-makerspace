CREATE TABLE subscription_audit_mismatch (
    id                    BIGSERIAL PRIMARY KEY,
    audit_run_at          TIMESTAMPTZ NOT NULL,
    user_id               BIGINT NOT NULL,
    stripe_subscription_id VARCHAR(255) NOT NULL,
    field_name            VARCHAR(100) NOT NULL,
    local_value           TEXT,
    stripe_value          TEXT,
    resolved              BOOLEAN NOT NULL DEFAULT false,
    resolved_at           TIMESTAMPTZ
);

CREATE INDEX idx_audit_mismatch_resolved ON subscription_audit_mismatch (resolved, audit_run_at DESC);
