CREATE TABLE inbox_events
(
    event_id     UUID                        NOT NULL,
    event_type   VARCHAR(100)                NOT NULL,
    consumer     VARCHAR(200)                NOT NULL,
    processed_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_inbox_events PRIMARY KEY (event_id)
);

CREATE TABLE outbox_events
(
    id             UUID                        NOT NULL,
    aggregate_type VARCHAR(100)                NOT NULL,
    aggregate_id   VARCHAR(100),
    event_type     VARCHAR(100)                NOT NULL,
    topic          VARCHAR(200)                NOT NULL,
    event_key      VARCHAR(200),
    payload        JSONB                       NOT NULL,
    status         VARCHAR(20)                 NOT NULL,
    retry_count    INTEGER                     NOT NULL,
    created_at     TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    published_at   TIMESTAMP WITHOUT TIME ZONE,
    next_retry_at  TIMESTAMP WITHOUT TIME ZONE,
    last_error     TEXT,
    CONSTRAINT pk_outbox_events PRIMARY KEY (id)
);

CREATE INDEX idx_inbox_processed_at ON inbox_events (processed_at);

CREATE INDEX idx_outbox_aggregate ON outbox_events (aggregate_type, aggregate_id);

CREATE INDEX idx_outbox_status_retry ON outbox_events (status, next_retry_at, created_at);