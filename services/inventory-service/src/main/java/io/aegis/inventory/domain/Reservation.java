package io.aegis.inventory.domain;
import java.time.Instant;
import java.util.UUID;
public record Reservation(UUID reservationId,UUID purchaseId,String sku,int quantity,long unitPriceMinor,
                          long totalAmountMinor,String currency,State state,Instant createdAt,Instant expiresAt) {
    public enum State { ACTIVE,COMMITTED,RELEASED,EXPIRED }
}
