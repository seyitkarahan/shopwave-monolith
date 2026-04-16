ALTER TABLE orders
    ADD COLUMN idempotency_key VARCHAR(100);

CREATE UNIQUE INDEX ux_orders_idempotency_key
    ON orders (idempotency_key)
    WHERE idempotency_key IS NOT NULL;
