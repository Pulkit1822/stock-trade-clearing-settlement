-- V1__initial_schema.sql
-- Initializes the trades table for the clearing platform

CREATE TABLE IF NOT EXISTS trades (
    id UUID PRIMARY KEY,
    buyer_account VARCHAR(50) NOT NULL,
    seller_account VARCHAR(50) NOT NULL,
    instrument_id VARCHAR(50) NOT NULL,
    quantity NUMERIC(19,4) NOT NULL,
    price NUMERIC(19,4) NOT NULL,
    trade_date TIMESTAMP NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for querying trades by account (used frequently in clearing)
CREATE INDEX IF NOT EXISTS idx_trades_buyer_account ON trades(buyer_account);
CREATE INDEX IF NOT EXISTS idx_trades_seller_account ON trades(seller_account);

-- Index for querying trades by status (e.g., finding SUBMITTED but not processed)
CREATE INDEX IF NOT EXISTS idx_trades_status ON trades(status);
