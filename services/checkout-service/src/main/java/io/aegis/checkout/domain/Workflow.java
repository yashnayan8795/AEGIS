package io.aegis.checkout.domain;

import java.time.Instant;
import java.util.UUID;

/** Persistence metadata only; not a Saga executor. */
public record Workflow(UUID purchaseId, UUID reservationId, UUID paymentId, UUID refundId,
                       UUID notificationId, long revision, long generation,
                       UUID leaseOwner, Instant leaseUntil, boolean dispatchPaused) {
    public record Ids(UUID purchase, UUID reservation, UUID payment, UUID refund, UUID notification) {
        public Ids { java.util.Objects.requireNonNull(purchase); java.util.Objects.requireNonNull(reservation);
            java.util.Objects.requireNonNull(payment); java.util.Objects.requireNonNull(refund); java.util.Objects.requireNonNull(notification); }
        public static Ids allocate() { return new Ids(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID()); }
    }
}
