-- Reference migration for Flyway/Liquibase users.
-- Copy this file to your db/migration/ folder.
-- Set mediator.event-store.auto-create-schema=false when using this.

CREATE TABLE IF NOT EXISTS domain_events (
    event_id        VARCHAR(36) PRIMARY KEY,
    event_type      VARCHAR(255) NOT NULL,
    payload         JSONB NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    stored_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    correlation_id  VARCHAR(36),
    causation_id    VARCHAR(36),
    metadata        JSONB DEFAULT '{}',
    aggregate_type  VARCHAR(255),
    aggregate_id    VARCHAR(255),
    sequence_number BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_domain_events_aggregate_sequence
    ON domain_events (aggregate_type, aggregate_id, sequence_number)
    WHERE sequence_number IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_domain_events_aggregate
    ON domain_events (aggregate_type, aggregate_id, sequence_number);

CREATE INDEX IF NOT EXISTS idx_domain_events_type
    ON domain_events (event_type);

CREATE INDEX IF NOT EXISTS idx_domain_events_correlation
    ON domain_events (correlation_id);

CREATE INDEX IF NOT EXISTS idx_domain_events_occurred
    ON domain_events (occurred_at);
