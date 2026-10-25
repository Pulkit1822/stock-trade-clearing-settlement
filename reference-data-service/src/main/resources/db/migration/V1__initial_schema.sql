-- V1__initial_schema.sql
-- Initializes instruments and participants tables for the reference data service

CREATE TABLE IF NOT EXISTS instruments (
    id UUID PRIMARY KEY,
    instrument_id VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    asset_class VARCHAR(30) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_instruments_instrument_id ON instruments(instrument_id);
CREATE INDEX IF NOT EXISTS idx_instruments_status ON instruments(status);

CREATE TABLE IF NOT EXISTS participants (
    id UUID PRIMARY KEY,
    participant_id VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_participants_participant_id ON participants(participant_id);
CREATE INDEX IF NOT EXISTS idx_participants_status ON participants(status);
