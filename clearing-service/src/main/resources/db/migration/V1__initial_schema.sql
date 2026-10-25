-- V1__initial_schema.sql
-- Initializes the settlements table for the clearing service

CREATE TABLE IF NOT EXISTS settlements (
    id UUID PRIMARY KEY,
    trade_id VARCHAR(36) NOT NULL UNIQUE,
    buyer_account_id VARCHAR(50),
    seller_account_id VARCHAR(50),
    instrument_id VARCHAR(50),
    buyer_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    seller_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(30) NOT NULL,
    failure_reason VARCHAR(500),
    settled_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Look up settlement by trade (primary access pattern)
CREATE INDEX IF NOT EXISTS idx_settlements_trade_id ON settlements(trade_id);

-- Filter by status (e.g., find all PENDING settlements)
CREATE INDEX IF NOT EXISTS idx_settlements_status ON settlements(status);

-- Query settlements by participant account
CREATE INDEX IF NOT EXISTS idx_settlements_buyer_account ON settlements(buyer_account_id);
CREATE INDEX IF NOT EXISTS idx_settlements_seller_account ON settlements(seller_account_id);
