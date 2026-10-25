-- V2__rename_account_columns.sql
-- Renames columns in trades table to match standardized naming convention

-- Rename primary data columns
ALTER TABLE trades RENAME COLUMN buyer_account TO buyer_account_id;
ALTER TABLE trades RENAME COLUMN seller_account TO seller_account_id;

-- Index names do not strictly have to change for JPA validation, 
-- but we rename them for DBA clarity and consistency across the platform.
ALTER INDEX idx_trades_buyer_account RENAME TO idx_trades_buyer_account_id;
ALTER INDEX idx_trades_seller_account RENAME TO idx_trades_seller_account_id;
