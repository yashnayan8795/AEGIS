CREATE TABLE notification_receipt (
    notification_id uuid PRIMARY KEY,
    purchase_id uuid NOT NULL,
    kind varchar(32) NOT NULL CHECK (kind = 'PURCHASE_CONFIRMED'),
    state varchar(16) NOT NULL DEFAULT 'STORED' CHECK (state = 'STORED'),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
