CREATE TABLE payment (
    payment_id uuid PRIMARY KEY,
    purchase_id uuid NOT NULL UNIQUE,
    reservation_id uuid NOT NULL,
    amount_minor bigint NOT NULL CHECK (amount_minor > 0),
    currency varchar(3) NOT NULL CHECK (currency = 'INR'),
    state varchar(16) NOT NULL CHECK (state IN ('PENDING', 'SUCCEEDED', 'DECLINED')),
    decline_code varchar(64),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL CHECK (updated_at >= created_at),
    CONSTRAINT payment_decline_consistency CHECK (
        (state = 'DECLINED' AND decline_code IS NOT NULL AND decline_code = 'SIMULATED_DECLINE')
        OR (state IN ('PENDING', 'SUCCEEDED') AND decline_code IS NULL)
    )
);
