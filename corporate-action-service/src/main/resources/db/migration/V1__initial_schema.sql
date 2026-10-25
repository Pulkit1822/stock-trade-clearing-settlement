-- V1__initial_schema.sql
-- Initializes the corporate_actions table for the corporate action service

CREATE TABLE IF NOT EXISTS corporate_actions (
    id UUID PRIMARY KEY,
    corporate_action_id VARCHAR(36) NOT NULL UNIQUE,
    instrument_id VARCHAR(20) NOT NULL,
    action_type VARCHAR(20) NOT NULL,
    action_value NUMERIC(19,4) NOT NULL,
    status VARCHAR(20) NOT NULL,
    published_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_corporate_actions_corporate_action_id ON corporate_actions(corporate_action_id);
CREATE INDEX IF NOT EXISTS idx_corporate_actions_instrument_id ON corporate_actions(instrument_id);
CREATE INDEX IF NOT EXISTS idx_corporate_actions_status ON corporate_actions(status);
