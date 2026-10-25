CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Index to quickly find unpublished events, if we were tracking 'published' status.
-- In our design, we'll delete the event once published. So we'll index created_at for ordered polling.
CREATE INDEX idx_outbox_events_created_at ON outbox_events(created_at);
