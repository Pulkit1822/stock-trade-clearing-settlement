-- V2__add_accounts_table.sql
-- Adds the accounts table to the reference data service.
--
-- Account represents a trading account owned by a Participant.
-- A single participant may have multiple accounts (e.g. USD account, equity account).
-- The balance field tracks available cash; position quantities are tracked separately
-- in position-service.

CREATE TABLE IF NOT EXISTS accounts (
    id          UUID          PRIMARY KEY,
    account_id  VARCHAR(50)   NOT NULL UNIQUE,
    participant_id VARCHAR(50) NOT NULL,
    balance     NUMERIC(19,4) NOT NULL DEFAULT 0,
    currency    VARCHAR(10)   NOT NULL DEFAULT 'USD',
    status      VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Referential integrity: account must belong to a known participant.
    CONSTRAINT fk_accounts_participant
        FOREIGN KEY (participant_id)
        REFERENCES participants(participant_id)
        ON DELETE RESTRICT
);

-- Fast lookup for account-level queries (e.g. "get account by accountId")
CREATE INDEX IF NOT EXISTS idx_accounts_account_id
    ON accounts(account_id);

-- Fast lookup for all accounts owned by a participant
CREATE INDEX IF NOT EXISTS idx_accounts_participant_id
    ON accounts(participant_id);

-- Optional: fast filter for ACTIVE accounts only
CREATE INDEX IF NOT EXISTS idx_accounts_status
    ON accounts(status);
