ALTER TABLE payment ADD CONSTRAINT payment_finite_times CHECK (
    isfinite(created_at) AND isfinite(updated_at)
    AND created_at >= timestamptz '0001-01-01 00:00:00+00'
    AND updated_at < timestamptz '10000-01-01 00:00:00+00'
);
CREATE TABLE charge_policy (
    payment_id uuid PRIMARY KEY REFERENCES payment(payment_id),
    policy_version varchar(64) NOT NULL CHECK(length(policy_version)>0),
    scenario varchar(64) NOT NULL CHECK(length(scenario)>0),
    chosen_outcome varchar(12) NOT NULL CHECK(chosen_outcome IN ('SUCCEEDED','DECLINED'))
);
CREATE TABLE refund (
    refund_id uuid PRIMARY KEY,
    payment_id uuid NOT NULL UNIQUE REFERENCES payment(payment_id),
    amount_minor bigint NOT NULL CHECK(amount_minor>0),
    currency varchar(3) NOT NULL CHECK(currency='INR'),
    reason_code varchar(40) NOT NULL CHECK(reason_code='RESERVATION_EXPIRED'),
    state varchar(12) NOT NULL CHECK(state IN ('PENDING','SUCCEEDED','FAILED')),
    policy_version varchar(64) NOT NULL CHECK(length(policy_version)>0),
    scenario varchar(64) NOT NULL CHECK(length(scenario)>0),
    chosen_outcome varchar(12) NOT NULL CHECK(chosen_outcome IN ('SUCCEEDED','FAILED')),
    created_at timestamptz NOT NULL CHECK(isfinite(created_at) AND created_at >= timestamptz '0001-01-01 00:00:00+00'),
    updated_at timestamptz NOT NULL CHECK(isfinite(updated_at) AND updated_at>=created_at AND updated_at<timestamptz '10000-01-01 00:00:00+00'),
    CHECK(state='PENDING' OR state=chosen_outcome)
);
CREATE FUNCTION payment_identity_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.payment_id,NEW.purchase_id,NEW.reservation_id,NEW.amount_minor,NEW.currency,NEW.created_at)
      IS DISTINCT FROM ROW(OLD.payment_id,OLD.purchase_id,OLD.reservation_id,OLD.amount_minor,OLD.currency,OLD.created_at)
      OR (OLD.state<>'PENDING' AND NEW.state<>OLD.state) OR NEW.updated_at<OLD.updated_at THEN
      RAISE EXCEPTION 'immutable charge or terminal outcome' USING ERRCODE='23514';
    END IF;
    IF EXISTS(SELECT 1 FROM charge_policy WHERE payment_id=NEW.payment_id AND NEW.state<>'PENDING' AND chosen_outcome<>NEW.state) THEN
      RAISE EXCEPTION 'outcome contradicts retained policy' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER payment_guard BEFORE UPDATE ON payment FOR EACH ROW EXECUTE FUNCTION payment_identity_guard();
CREATE FUNCTION charge_policy_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE current_state varchar(16);
BEGIN
    IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'immutable policy' USING ERRCODE='23514'; END IF;
    SELECT state INTO current_state FROM payment WHERE payment_id=NEW.payment_id FOR UPDATE;
    IF current_state<>'PENDING' AND current_state<>NEW.chosen_outcome THEN
      RAISE EXCEPTION 'policy contradicts charge' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER policy_guard BEFORE INSERT OR UPDATE OR DELETE ON charge_policy FOR EACH ROW EXECUTE FUNCTION charge_policy_guard();
CREATE FUNCTION refund_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE charge payment%ROWTYPE;
BEGIN
    SELECT * INTO charge FROM payment WHERE payment_id=NEW.payment_id FOR UPDATE;
    IF charge.state IS DISTINCT FROM 'SUCCEEDED' OR charge.amount_minor IS DISTINCT FROM NEW.amount_minor OR charge.currency IS DISTINCT FROM NEW.currency THEN
      RAISE EXCEPTION 'refund requires matching successful charge' USING ERRCODE='23514';
    END IF;
    IF TG_OP='UPDATE' AND (ROW(NEW.refund_id,NEW.payment_id,NEW.amount_minor,NEW.currency,NEW.reason_code,NEW.policy_version,NEW.scenario,NEW.chosen_outcome,NEW.created_at)
      IS DISTINCT FROM ROW(OLD.refund_id,OLD.payment_id,OLD.amount_minor,OLD.currency,OLD.reason_code,OLD.policy_version,OLD.scenario,OLD.chosen_outcome,OLD.created_at)
      OR (OLD.state<>'PENDING' AND NEW.state<>OLD.state) OR NEW.updated_at<OLD.updated_at) THEN
      RAISE EXCEPTION 'immutable refund or terminal outcome' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER refund_guard BEFORE INSERT OR UPDATE ON refund FOR EACH ROW EXECUTE FUNCTION refund_guard();
