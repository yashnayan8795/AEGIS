-- V2 held snapshots, not allocated balances. Never invent an accounting history.
DO $$ BEGIN
    IF EXISTS(SELECT 1 FROM reservation) THEN
        RAISE EXCEPTION 'Existing reservation snapshots require reviewed accounting migration' USING ERRCODE='23514';
    END IF;
END $$;
CREATE TABLE reservation_identity (
    reservation_id uuid PRIMARY KEY,
    purchase_id uuid NOT NULL UNIQUE,
    sku varchar(64) NOT NULL CHECK(sku ~ '^[A-Z0-9][A-Z0-9_-]{0,63}$'),
    quantity integer NOT NULL CHECK(quantity>0),
    UNIQUE(reservation_id,purchase_id,sku,quantity)
);
-- Preserve retained V2 negative outcomes; no new business result is synthesized.
INSERT INTO reservation_identity SELECT reservation_id,purchase_id,sku,quantity FROM reservation_request;
ALTER TABLE reservation_request ADD CONSTRAINT request_identity FOREIGN KEY(reservation_id,purchase_id,sku,quantity)
    REFERENCES reservation_identity(reservation_id,purchase_id,sku,quantity);
ALTER TABLE reservation_identity ADD CONSTRAINT identity_result FOREIGN KEY(reservation_id)
    REFERENCES reservation_request(reservation_id) DEFERRABLE INITIALLY DEFERRED;
CREATE TRIGGER identity_immutable BEFORE UPDATE OR DELETE ON reservation_identity FOR EACH ROW EXECUTE FUNCTION reservation_request_immutable();
ALTER TABLE catalog_item ADD COLUMN initial_quantity bigint;
ALTER TABLE catalog_item ADD COLUMN reserved_quantity bigint NOT NULL DEFAULT 0;
ALTER TABLE catalog_item ADD COLUMN sold_quantity bigint NOT NULL DEFAULT 0;
UPDATE catalog_item SET initial_quantity=available_quantity;
ALTER TABLE catalog_item ALTER COLUMN initial_quantity SET NOT NULL;
ALTER TABLE catalog_item ADD CONSTRAINT stock_nonnegative CHECK(initial_quantity>=0 AND reserved_quantity>=0 AND sold_quantity>=0);
ALTER TABLE catalog_item ADD CONSTRAINT stock_conservation CHECK(available_quantity::numeric+reserved_quantity::numeric+sold_quantity::numeric=initial_quantity::numeric);
CREATE FUNCTION catalog_accounting_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='INSERT' THEN
        IF NEW.initial_quantity IS NULL THEN
            NEW.initial_quantity := NEW.available_quantity::numeric+NEW.reserved_quantity::numeric+NEW.sold_quantity::numeric;
        END IF;
    ELSIF ROW(NEW.sku,NEW.initial_quantity) IS DISTINCT FROM ROW(OLD.sku,OLD.initial_quantity) THEN
        RAISE EXCEPTION 'Immutable SKU/initial stock; no adjustment protocol' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER catalog_accounting BEFORE INSERT OR UPDATE ON catalog_item FOR EACH ROW EXECUTE FUNCTION catalog_accounting_guard();
CREATE INDEX reservation_accounting ON reservation(sku,state) INCLUDE(quantity) WHERE state IN ('ACTIVE','COMMITTED');
CREATE FUNCTION reservation_stock_consistency() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE item varchar(64); held bigint; sold bigint; expected_held numeric; expected_sold numeric;
BEGIN
    IF TG_OP='DELETE' THEN item := OLD.sku; ELSE item := NEW.sku; END IF;
    SELECT reserved_quantity,sold_quantity INTO held,sold FROM catalog_item WHERE sku=item FOR UPDATE;
    IF NOT FOUND THEN RETURN NULL; END IF;
    SELECT COALESCE(sum(quantity::numeric) FILTER(WHERE state='ACTIVE'),0),
           COALESCE(sum(quantity::numeric) FILTER(WHERE state='COMMITTED'),0)
      INTO expected_held,expected_sold FROM reservation WHERE sku=item AND state IN ('ACTIVE','COMMITTED');
    IF held::numeric<>expected_held OR sold::numeric<>expected_sold THEN
        RAISE EXCEPTION 'Reservation/stock accounting mismatch' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER catalog_stock_consistency AFTER INSERT OR UPDATE ON catalog_item
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION reservation_stock_consistency();
CREATE CONSTRAINT TRIGGER reservation_stock_consistency AFTER INSERT OR UPDATE OR DELETE ON reservation
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION reservation_stock_consistency();
