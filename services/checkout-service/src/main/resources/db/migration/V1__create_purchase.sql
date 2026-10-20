-- Representation constraints only. M1.3 must separately establish evidence and guard transitions.
CREATE TABLE purchase (
    purchase_id uuid PRIMARY KEY,
    sku varchar(64) NOT NULL CHECK (sku ~ '^[A-Z0-9][A-Z0-9_-]{0,63}$'),
    quantity integer NOT NULL CHECK (quantity > 0),
    amount_minor bigint,
    currency varchar(3),
    state varchar(24) NOT NULL CHECK (state IN ('PROCESSING','COMPENSATING','CONFIRMED','FAILED','REQUIRES_ATTENTION')),
    step varchar(32) NOT NULL CHECK (step IN ('RESERVATION_PENDING','PAYMENT_PENDING','INVENTORY_COMMIT_PENDING','COMPENSATION_PENDING','SETTLED')),
    compensation_pending boolean NOT NULL,
    payment_outcome_known boolean NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL CHECK (updated_at >= created_at),
    CONSTRAINT purchase_finite_times CHECK (isfinite(created_at) AND isfinite(updated_at)),
    -- Explicit null checks prevent PostgreSQL's CHECK/UNKNOWN rule admitting partial prices.
    CONSTRAINT purchase_price_pair CHECK (
        (amount_minor IS NULL AND currency IS NULL)
        OR (amount_minor IS NOT NULL AND amount_minor > 0 AND currency IS NOT NULL AND currency = 'INR')
    ),
    CONSTRAINT purchase_known_payment_priced CHECK (NOT payment_outcome_known OR amount_minor IS NOT NULL),
    CONSTRAINT purchase_snapshot CHECK (
        (state IN ('PROCESSING','REQUIRES_ATTENTION') AND NOT compensation_pending AND (
            (step = 'RESERVATION_PENDING' AND NOT payment_outcome_known AND amount_minor IS NULL)
            OR (step = 'PAYMENT_PENDING' AND NOT payment_outcome_known AND amount_minor IS NOT NULL)
            OR (step = 'INVENTORY_COMMIT_PENDING' AND payment_outcome_known AND amount_minor IS NOT NULL)
        ))
        OR (state IN ('COMPENSATING','REQUIRES_ATTENTION') AND step = 'COMPENSATION_PENDING' AND compensation_pending)
        OR (state = 'CONFIRMED' AND step = 'SETTLED' AND payment_outcome_known AND NOT compensation_pending AND amount_minor IS NOT NULL)
        OR (state = 'FAILED' AND step = 'SETTLED' AND NOT compensation_pending)
    )
);
