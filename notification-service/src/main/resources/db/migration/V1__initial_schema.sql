-- V1__initial_schema.sql
-- Initializes the notifications table for the notification service

CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY,
    trade_id VARCHAR(36) NOT NULL,
    account_id VARCHAR(50) NOT NULL,
    message VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Look up all notifications for a trade (buyer + seller records)
CREATE INDEX IF NOT EXISTS idx_notifications_trade_id ON notifications(trade_id);

-- Look up notification history for an account
CREATE INDEX IF NOT EXISTS idx_notifications_account_id ON notifications(account_id);

-- Find unsent or failed notifications for retry
CREATE INDEX IF NOT EXISTS idx_notifications_status ON notifications(status);
