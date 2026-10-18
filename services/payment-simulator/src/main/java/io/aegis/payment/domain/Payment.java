package io.aegis.payment.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/** Stored read model, not a charge executor. Null declineCode is a required wire property. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record Payment(UUID paymentId, UUID purchaseId, UUID reservationId, long amountMinor,
                      String currency, State state, String declineCode, Instant createdAt, Instant updatedAt) {
    public enum State { PENDING, SUCCEEDED, DECLINED }
}
