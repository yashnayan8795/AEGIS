package io.aegis.payment.domain;
import java.time.Instant;
import java.util.UUID;
public record RefundRecord(UUID refundId,UUID paymentId,long amountMinor,String currency,String reasonCode,
                           State state,SyntheticPolicy policy,Instant createdAt,Instant updatedAt) {
    public enum State { PENDING,SUCCEEDED,FAILED }
}
