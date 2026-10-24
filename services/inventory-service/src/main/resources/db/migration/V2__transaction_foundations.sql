CREATE TABLE reservation_request (
    reservation_id uuid PRIMARY KEY,
    purchase_id uuid NOT NULL UNIQUE,
    sku varchar(64) NOT NULL CHECK(sku ~ '^[A-Z0-9][A-Z0-9_-]{0,63}$'),
    quantity integer NOT NULL CHECK(quantity>0),
    outcome varchar(12) NOT NULL CHECK(outcome IN ('CREATED','REJECTED')),
    error_code varchar(32),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp() CHECK(isfinite(created_at)),
    UNIQUE(reservation_id,purchase_id,sku,quantity),
    CHECK((outcome='CREATED' AND error_code IS NULL) OR
          (outcome='REJECTED' AND error_code IS NOT NULL AND error_code IN ('ITEM_NOT_FOUND','MONETARY_OVERFLOW','INSUFFICIENT_STOCK')))
);
CREATE TABLE reservation (
    reservation_id uuid PRIMARY KEY,
    purchase_id uuid NOT NULL UNIQUE,
    sku varchar(64) NOT NULL REFERENCES catalog_item(sku),
    quantity integer NOT NULL CHECK(quantity>0),
    unit_price_minor bigint NOT NULL CHECK(unit_price_minor>0),
    total_amount_minor bigint NOT NULL CHECK(total_amount_minor>0),
    currency varchar(3) NOT NULL CHECK(currency='INR'),
    state varchar(12) NOT NULL CHECK(state IN ('ACTIVE','COMMITTED','RELEASED','EXPIRED')),
    created_at timestamptz NOT NULL CHECK(isfinite(created_at) AND created_at>=timestamptz '0001-01-01 00:00:00+00'),
    expires_at timestamptz NOT NULL CHECK(isfinite(expires_at) AND expires_at>created_at AND expires_at<timestamptz '10000-01-01 00:00:00+00'),
    CHECK(total_amount_minor::numeric=unit_price_minor::numeric*quantity),
    FOREIGN KEY(reservation_id,purchase_id,sku,quantity) REFERENCES reservation_request(reservation_id,purchase_id,sku,quantity)
);
CREATE INDEX reservation_expiry ON reservation(expires_at) WHERE state='ACTIVE';
CREATE FUNCTION reservation_request_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'immutable request outcome' USING ERRCODE='23514'; END $$;
CREATE TRIGGER request_immutable BEFORE UPDATE OR DELETE ON reservation_request FOR EACH ROW EXECUTE FUNCTION reservation_request_immutable();
CREATE FUNCTION reservation_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.reservation_id,NEW.purchase_id,NEW.sku,NEW.quantity,NEW.unit_price_minor,NEW.total_amount_minor,NEW.currency,NEW.created_at,NEW.expires_at)
       IS DISTINCT FROM ROW(OLD.reservation_id,OLD.purchase_id,OLD.sku,OLD.quantity,OLD.unit_price_minor,OLD.total_amount_minor,OLD.currency,OLD.created_at,OLD.expires_at)
       OR (OLD.state<>'ACTIVE' AND NEW.state<>OLD.state) THEN
       RAISE EXCEPTION 'immutable reservation or terminal state' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER reservation_guard BEFORE UPDATE ON reservation FOR EACH ROW EXECUTE FUNCTION reservation_guard();
CREATE FUNCTION reservation_outcome_consistency() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE request_id uuid; result varchar(12); present boolean;
BEGIN
    request_id := NEW.reservation_id;
    SELECT outcome INTO result FROM reservation_request WHERE reservation_id=request_id;
    SELECT EXISTS(SELECT 1 FROM reservation WHERE reservation_id=request_id) INTO present;
    IF (result='CREATED') IS DISTINCT FROM present THEN
       RAISE EXCEPTION 'request/reservation mismatch' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER request_consistency AFTER INSERT ON reservation_request DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION reservation_outcome_consistency();
CREATE CONSTRAINT TRIGGER reservation_consistency AFTER INSERT ON reservation DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION reservation_outcome_consistency();
