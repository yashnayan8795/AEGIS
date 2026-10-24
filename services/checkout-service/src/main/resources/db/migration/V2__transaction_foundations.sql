ALTER TABLE purchase ADD CONSTRAINT purchase_wire_time_range CHECK (
    created_at>=timestamptz '0001-01-01 00:00:00+00' AND updated_at<timestamptz '10000-01-01 00:00:00+00'
);
CREATE TABLE purchase_key (
    scope varchar(40) NOT NULL CHECK (scope = 'v1:synthetic:purchase'),
    client_key uuid NOT NULL,
    purchase_id uuid NOT NULL UNIQUE REFERENCES purchase(purchase_id) DEFERRABLE INITIALLY DEFERRED,
    sku varchar(64) NOT NULL CHECK (sku ~ '^[A-Z0-9][A-Z0-9_-]{0,63}$'),
    quantity integer NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (scope, client_key)
);
CREATE TABLE purchase_workflow (
    purchase_id uuid PRIMARY KEY REFERENCES purchase(purchase_id),
    reservation_id uuid NOT NULL UNIQUE,
    payment_id uuid NOT NULL UNIQUE,
    refund_id uuid NOT NULL UNIQUE,
    notification_id uuid NOT NULL UNIQUE,
    revision bigint NOT NULL DEFAULT 0 CHECK (revision >= 0),
    generation bigint NOT NULL DEFAULT 0 CHECK (generation >= 0),
    lease_owner uuid,
    lease_until timestamptz,
    dispatch_paused boolean NOT NULL DEFAULT false,
    attention_reason varchar(80),
    next_attempt_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    reservation_unresolved boolean NOT NULL DEFAULT true,
    payment_unresolved boolean NOT NULL DEFAULT false,
    cleanup_required boolean NOT NULL DEFAULT false,
    refund_required boolean NOT NULL DEFAULT false,
    CHECK ((lease_owner IS NULL) = (lease_until IS NULL)),
    CHECK (lease_until IS NULL OR isfinite(lease_until)),
    CHECK (isfinite(next_attempt_at))
);
CREATE INDEX purchase_workflow_due ON purchase_workflow(next_attempt_at) WHERE NOT dispatch_paused;
CREATE TABLE workflow_intent (
    purchase_id uuid NOT NULL REFERENCES purchase_workflow(purchase_id),
    action varchar(12) NOT NULL CHECK (action IN ('RESERVE','CHARGE','COMMIT','RELEASE','REFUND')),
    operation_id uuid NOT NULL UNIQUE,
    sku varchar(64), quantity integer, amount_minor bigint, currency varchar(3),
    reason_code varchar(40),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp() CHECK (isfinite(created_at)),
    PRIMARY KEY(purchase_id, action),
    CHECK ((action='RESERVE' AND sku IS NOT NULL AND sku ~ '^[A-Z0-9][A-Z0-9_-]{0,63}$'
             AND quantity IS NOT NULL AND quantity>0 AND amount_minor IS NULL AND currency IS NULL AND reason_code IS NULL)
        OR (action='CHARGE' AND sku IS NULL AND quantity IS NULL AND amount_minor IS NOT NULL AND amount_minor>0 AND currency IS NOT NULL AND currency='INR' AND reason_code IS NULL)
        OR (action IN ('COMMIT','RELEASE') AND sku IS NULL AND quantity IS NULL AND amount_minor IS NULL AND currency IS NULL AND reason_code IS NULL)
        OR (action='REFUND' AND sku IS NULL AND quantity IS NULL AND amount_minor IS NOT NULL AND amount_minor>0 AND currency IS NOT NULL AND currency='INR' AND reason_code IS NOT NULL AND reason_code='RESERVATION_EXPIRED'))
);
CREATE TABLE workflow_observation (
    purchase_id uuid NOT NULL,
    action varchar(12) NOT NULL,
    outcome varchar(32) NOT NULL,
    observed_at timestamptz NOT NULL CHECK (isfinite(observed_at)),
    PRIMARY KEY(purchase_id,action),
    FOREIGN KEY(purchase_id,action) REFERENCES workflow_intent(purchase_id,action),
    CHECK ((action='RESERVE' AND outcome IN ('ACTIVE','COMMITTED','RELEASED','EXPIRED','REJECTED'))
        OR(action='CHARGE' AND outcome IN ('PENDING','SUCCEEDED','DECLINED'))
        OR(action='COMMIT' AND outcome IN ('COMMITTED','RELEASED','EXPIRED'))
        OR(action='RELEASE' AND outcome IN ('RELEASED','EXPIRED','COMMITTED'))
        OR(action='REFUND' AND outcome IN ('PENDING','SUCCEEDED','FAILED')))
);
CREATE TABLE notification_intent (
    purchase_id uuid PRIMARY KEY REFERENCES purchase_workflow(purchase_id),
    notification_id uuid NOT NULL UNIQUE,
    kind varchar(32) NOT NULL CHECK(kind='PURCHASE_CONFIRMED'),
    acknowledged_at timestamptz CHECK(acknowledged_at IS NULL OR isfinite(acknowledged_at))
);
CREATE FUNCTION checkout_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'immutable identity/payload' USING ERRCODE='23514';
END $$;
CREATE TRIGGER key_immutable BEFORE UPDATE OR DELETE ON purchase_key FOR EACH ROW EXECUTE FUNCTION checkout_immutable();
CREATE TRIGGER intent_immutable BEFORE UPDATE OR DELETE ON workflow_intent FOR EACH ROW EXECUTE FUNCTION checkout_immutable();
CREATE FUNCTION checkout_workflow_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.purchase_id,NEW.reservation_id,NEW.payment_id,NEW.refund_id,NEW.notification_id)
        IS DISTINCT FROM ROW(OLD.purchase_id,OLD.reservation_id,OLD.payment_id,OLD.refund_id,OLD.notification_id)
        OR NEW.revision <> OLD.revision+1 OR NEW.generation < OLD.generation THEN
        RAISE EXCEPTION 'invalid workflow identity/version change' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER workflow_guard BEFORE UPDATE ON purchase_workflow FOR EACH ROW EXECUTE FUNCTION checkout_workflow_guard();
CREATE FUNCTION checkout_observation_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.purchase_id<>OLD.purchase_id OR NEW.action<>OLD.action OR NEW.observed_at<OLD.observed_at
        OR (NEW.outcome<>OLD.outcome AND OLD.outcome NOT IN ('PENDING','ACTIVE'))
        OR (OLD.outcome='ACTIVE' AND NEW.outcome NOT IN ('ACTIVE','COMMITTED','RELEASED','EXPIRED')) THEN
        RAISE EXCEPTION 'regressive or contradictory evidence' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER observation_guard BEFORE UPDATE ON workflow_observation FOR EACH ROW EXECUTE FUNCTION checkout_observation_guard();

CREATE FUNCTION checkout_binding_consistency() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS(SELECT 1 FROM purchase WHERE purchase_id=NEW.purchase_id AND sku=NEW.sku AND quantity=NEW.quantity) THEN
      RAISE EXCEPTION 'binding payload differs from purchase' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER binding_consistency AFTER INSERT ON purchase_key DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION checkout_binding_consistency();
CREATE FUNCTION checkout_intent_binding() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE wf purchase_workflow%ROWTYPE;
BEGIN
    SELECT * INTO wf FROM purchase_workflow WHERE purchase_id=NEW.purchase_id;
    IF (NEW.action='RESERVE' AND NEW.operation_id IS DISTINCT FROM wf.reservation_id)
      OR (NEW.action='CHARGE' AND NEW.operation_id IS DISTINCT FROM wf.payment_id)
      OR (NEW.action='REFUND' AND NEW.operation_id IS DISTINCT FROM wf.refund_id) THEN
      RAISE EXCEPTION 'intent operation identity mismatch' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER intent_binding BEFORE INSERT ON workflow_intent FOR EACH ROW EXECUTE FUNCTION checkout_intent_binding();
CREATE FUNCTION notification_intent_binding() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS(SELECT 1 FROM purchase_workflow w JOIN purchase p USING(purchase_id)
        WHERE w.purchase_id=NEW.purchase_id AND w.notification_id=NEW.notification_id AND p.state='CONFIRMED') THEN
       RAISE EXCEPTION 'notification intent requires confirmed purchase and stable identity' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER notification_binding AFTER INSERT ON notification_intent DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION notification_intent_binding();

CREATE FUNCTION checkout_purchase_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS(SELECT 1 FROM purchase_workflow WHERE purchase_id=OLD.purchase_id) AND (
       ROW(NEW.purchase_id,NEW.sku,NEW.quantity,NEW.created_at) IS DISTINCT FROM ROW(OLD.purchase_id,OLD.sku,OLD.quantity,OLD.created_at)
       OR (OLD.amount_minor IS NOT NULL AND ROW(NEW.amount_minor,NEW.currency) IS DISTINCT FROM ROW(OLD.amount_minor,OLD.currency))
       OR (OLD.payment_outcome_known AND NOT NEW.payment_outcome_known)) THEN
       RAISE EXCEPTION 'immutable purchase identity or regressive knowledge' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER purchase_identity BEFORE UPDATE ON purchase FOR EACH ROW EXECUTE FUNCTION checkout_purchase_identity();
CREATE FUNCTION notification_identity_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.purchase_id,NEW.notification_id,NEW.kind) IS DISTINCT FROM ROW(OLD.purchase_id,OLD.notification_id,OLD.kind)
       OR (OLD.acknowledged_at IS NOT NULL AND NEW.acknowledged_at IS DISTINCT FROM OLD.acknowledged_at) THEN
       RAISE EXCEPTION 'immutable notification identity or acknowledgement' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER notification_identity BEFORE UPDATE ON notification_intent FOR EACH ROW EXECUTE FUNCTION notification_identity_guard();
