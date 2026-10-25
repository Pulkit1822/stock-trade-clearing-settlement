CREATE TABLE IF NOT EXISTS positions (
    id UUID PRIMARY KEY,
    account_id VARCHAR(50) NOT NULL,
    instrument_id VARCHAR(50) NOT NULL,
    quantity NUMERIC(19,4) NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Crucial unique constraint: An account can only have ONE active position record per instrument.
-- This ensures our PESSIMISTIC_WRITE locks operate on exactly one row and prevents double-counting.
ALTER TABLE positions ADD CONSTRAINT uc_account_instrument UNIQUE (account_id, instrument_id);

-- Fast lookup index for validation service when querying the seller's position
CREATE INDEX IF NOT EXISTS idx_positions_account_instrument ON positions(account_id, instrument_id);
