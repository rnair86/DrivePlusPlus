CREATE TABLE notification_preferences (
    user_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    channel VARCHAR(10) NOT NULL CHECK (channel IN ('EMAIL','SMS','PUSH')),
    enabled BOOLEAN NOT NULL DEFAULT true,
    quiet_hours_start TIME,
    quiet_hours_end TIME,
    PRIMARY KEY (user_id, event_type, channel)
);
