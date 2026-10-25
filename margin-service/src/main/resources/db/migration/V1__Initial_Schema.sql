CREATE TABLE collateral_accounts (
    id UUID PRIMARY KEY,
    account_id VARCHAR(50) NOT NULL UNIQUE,
    balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE margin_requirements (
    id UUID PRIMARY KEY,
    account_id VARCHAR(50) NOT NULL UNIQUE,
    required_margin DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    last_calculated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE margin_calls (
    id UUID PRIMARY KEY,
    account_id VARCHAR(50) NOT NULL,
    deficit_amount DECIMAL(15, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_margin_call_status CHECK (status IN ('OPEN', 'RESOLVED'))
);

CREATE INDEX idx_margin_calls_account_status ON margin_calls(account_id, status);
