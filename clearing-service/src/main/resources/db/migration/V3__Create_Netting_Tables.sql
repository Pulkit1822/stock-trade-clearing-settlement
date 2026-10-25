CREATE TABLE netting_groups (
    id UUID PRIMARY KEY,
    business_date VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE net_settlement_obligations (
    id UUID PRIMARY KEY,
    netting_group_id UUID NOT NULL,
    account_id VARCHAR(50) NOT NULL,
    instrument_id VARCHAR(50) NOT NULL,
    net_quantity BIGINT NOT NULL,
    net_cash DECIMAL(18, 4) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_net_settlement_obligations_group FOREIGN KEY (netting_group_id) REFERENCES netting_groups (id) ON DELETE CASCADE
);

CREATE INDEX idx_net_settlement_obligations_account ON net_settlement_obligations(account_id);
