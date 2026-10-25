-- V2__rename_account_columns.sql
-- Renames columns in trades table to match standardized naming convention
-- Idempotent: skips each step if the column already has the target name

DO $$
BEGIN
  -- Rename buyer_account → buyer_account_id if not already done
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'trades' AND column_name = 'buyer_account'
  ) THEN
    ALTER TABLE trades RENAME COLUMN buyer_account TO buyer_account_id;
  END IF;

  -- Rename seller_account → seller_account_id if not already done
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'trades' AND column_name = 'seller_account'
  ) THEN
    ALTER TABLE trades RENAME COLUMN seller_account TO seller_account_id;
  END IF;

  -- Rename indexes only if they still have the old names
  IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_trades_buyer_account') THEN
    ALTER INDEX idx_trades_buyer_account RENAME TO idx_trades_buyer_account_id;
  END IF;

  IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_trades_seller_account') THEN
    ALTER INDEX idx_trades_seller_account RENAME TO idx_trades_seller_account_id;
  END IF;
END $$;
