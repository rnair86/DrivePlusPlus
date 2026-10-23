CREATE TABLE notification_log (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    source_event_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    channel VARCHAR(10) NOT NULL CHECK (channel IN ('EMAIL','SMS','PUSH')),
    status VARCHAR(30) NOT NULL CHECK (status IN ('SENT','FAILED','SUPPRESSED_BY_PREFERENCE')),
    provider_message_id VARCHAR(100),
    sent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    error_message TEXT,
    UNIQUE (source_event_id, user_id, channel)
);

CREATE TABLE preference_cache (
    user_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    channel VARCHAR(10) NOT NULL CHECK (channel IN ('EMAIL','SMS','PUSH')),
    enabled BOOLEAN NOT NULL DEFAULT true,
    PRIMARY KEY (user_id, event_type, channel)
);

CREATE TABLE templates (
    id UUID PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    channel VARCHAR(10) NOT NULL CHECK (channel IN ('EMAIL','SMS','PUSH')),
    subject_template VARCHAR(255),
    body_template TEXT NOT NULL,
    UNIQUE (event_type, channel)
);
