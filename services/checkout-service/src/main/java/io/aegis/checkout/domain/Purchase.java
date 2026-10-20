package io.aegis.checkout.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/** Durable query snapshot only; no transition or cross-service evidence is inferred here. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record Purchase(UUID purchaseId, String sku, int quantity, Long amountMinor, String currency,
                       State state, Step step, boolean compensationPending, boolean paymentOutcomeKnown,
                       Instant createdAt, Instant updatedAt) {
    public enum State { PROCESSING, COMPENSATING, CONFIRMED, FAILED, REQUIRES_ATTENTION }
    public enum Step { RESERVATION_PENDING, PAYMENT_PENDING, INVENTORY_COMMIT_PENDING, COMPENSATION_PENDING, SETTLED }
}
