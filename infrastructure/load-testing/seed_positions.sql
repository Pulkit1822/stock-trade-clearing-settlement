-- Seed Positions for Load Testing
-- Target 100 seller accounts with high quantities to avoid insufficient balance rejections during 1000 trades/minute tests.
DO $$
DECLARE
    i INTEGER;
BEGIN
    FOR i IN 1..100 LOOP
        INSERT INTO positions (id, account_id, instrument_id, quantity)
        VALUES (
            gen_random_uuid(),
            'LOAD-SELLER-' || i,
            'AAPL',
            1000000 -- 1 million quantity
        )
        ON CONFLICT DO NOTHING;
    END LOOP;
END $$;
