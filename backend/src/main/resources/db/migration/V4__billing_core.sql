-- Manual Migration

-- Link a User to its Stripe Customer. Nullable: created lazily on first checkout.
ALTER TABLE users ADD COLUMN stripe_customer_id VARCHAR(255) UNIQUE;
CREATE INDEX idx_users_stripe_customer ON users(stripe_customer_id);

-- Idempotency ledger. Every inbound Stripe event is recorded here exactly once.
CREATE TABLE stripe_event_log (
                                  event_id        VARCHAR(255) PRIMARY KEY,      -- evt_… from Stripe
                                  event_type      VARCHAR(100) NOT NULL,
                                  api_version     VARCHAR(50),
                                  livemode        BOOLEAN NOT NULL,
                                  received_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
                                  processed_at    TIMESTAMPTZ,
                                  status          VARCHAR(30) NOT NULL,          -- RECEIVED | PROCESSED | FAILED | SKIPPED
                                  failure_reason  TEXT,
                                  attempt_count   INT NOT NULL DEFAULT 0,
                                  source          VARCHAR(30) NOT NULL           -- EVENTBRIDGE | RECONCILIATION | MANUAL
);
CREATE INDEX idx_stripe_event_log_status ON stripe_event_log(status, received_at);
CREATE INDEX idx_stripe_event_log_type ON stripe_event_log(event_type, received_at DESC);

-- Plan catalog. Maps our membership tiers to Stripe Prices.
CREATE TABLE membership_plan (
                                 id                  BIGSERIAL PRIMARY KEY,
                                 code                VARCHAR(50) UNIQUE NOT NULL,   -- MONTHLY, ANNUAL, STUDENT, DAY_PASS
                                 display_name        VARCHAR(100) NOT NULL,
                                 stripe_price_id     VARCHAR(255) UNIQUE NOT NULL,
                                 stripe_product_id   VARCHAR(255) NOT NULL,
                                 billing_interval    VARCHAR(20),                   -- MONTH | YEAR | NULL for one-off
                                 amount_cents        INT NOT NULL,
                                 currency            CHAR(3) NOT NULL DEFAULT 'usd',
                                 grants_role         VARCHAR(30) REFERENCES roles(code),  -- MEMBER | STUDENT | RENTEE
                                 active              BOOLEAN NOT NULL DEFAULT TRUE,
                                 created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Local mirror of the Stripe subscription. Stripe is authoritative.
CREATE TABLE membership (
                            id                      BIGSERIAL PRIMARY KEY,
                            user_id                 BIGINT NOT NULL REFERENCES users(id),
                            plan_id                 BIGINT NOT NULL REFERENCES membership_plan(id),
                            stripe_subscription_id  VARCHAR(255) UNIQUE,       -- NULL for one-off day passes
                            status                  VARCHAR(30) NOT NULL,      -- see §6
                            current_period_start    TIMESTAMPTZ,
                            current_period_end      TIMESTAMPTZ,
                            cancel_at_period_end    BOOLEAN NOT NULL DEFAULT FALSE,
                            canceled_at             TIMESTAMPTZ,
                            grace_period_ends_at    TIMESTAMPTZ,
                            stripe_updated_at       TIMESTAMPTZ,               -- for out-of-order rejection, §7
                            created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
                            updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_membership_user ON membership(user_id);
CREATE INDEX idx_membership_status_period ON membership(status, current_period_end);
-- At most one non-terminal membership per user.
CREATE UNIQUE INDEX uq_membership_active_per_user
    ON membership(user_id) WHERE status IN ('ACTIVE','PAST_DUE','TRIALING','GRACE');

-- Immutable payment ledger. Append-only; never UPDATE a row's amount.
CREATE TABLE payment_record (
                                id                          BIGSERIAL PRIMARY KEY,
                                user_id                     BIGINT REFERENCES users(id),
                                membership_id               BIGINT REFERENCES membership(id),
                                reservation_id              BIGINT REFERENCES reservations(id),
                                stripe_payment_intent_id    VARCHAR(255) UNIQUE,
                                stripe_invoice_id           VARCHAR(255),
                                stripe_charge_id            VARCHAR(255),
                                kind                        VARCHAR(30) NOT NULL,  -- MEMBERSHIP | DAY_PASS | CLASS | RENTAL | REFUND
                                amount_cents                INT NOT NULL,          -- negative for refunds
                                currency                    CHAR(3) NOT NULL DEFAULT 'usd',
                                status                      VARCHAR(30) NOT NULL,  -- PENDING | SUCCEEDED | FAILED | REFUNDED
                                description                 VARCHAR(255),
                                occurred_at                 TIMESTAMPTZ NOT NULL,
                                created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payment_record_user ON payment_record(user_id, occurred_at DESC);
CREATE INDEX idx_payment_record_invoice ON payment_record(stripe_invoice_id);