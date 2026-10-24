package io.aegis.inventory.infrastructure.persistence;

import io.aegis.inventory.domain.Reservation;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Repository
public class ReservationRepository {
    public record RequestOutcome(UUID reservationId,UUID purchaseId,String sku,int quantity,String outcome,String errorCode) {}
    public static class RequestConflict extends RuntimeException {}
    private final JdbcClient jdbc;
    public ReservationRepository(JdbcClient jdbc) { this.jdbc=jdbc; }
    public Optional<RequestOutcome> request(UUID id) {
        return jdbc.sql("SELECT * FROM reservation_request WHERE reservation_id=:id").param("id",id).query((r,n)->new RequestOutcome(
            r.getObject("reservation_id",UUID.class),r.getObject("purchase_id",UUID.class),r.getString("sku"),r.getInt("quantity"),r.getString("outcome"),r.getString("error_code"))).optional();
    }
    /** Retains a decided rejection; does not make stock decisions or allocate inventory. */
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public RequestOutcome retainRejection(UUID id,UUID purchase,String sku,int quantity,String error) {
        jdbc.sql("""
            INSERT INTO reservation_identity(reservation_id,purchase_id,sku,quantity)
            VALUES (:id,:purchase,:sku,:quantity) ON CONFLICT DO NOTHING
            """).param("id",id).param("purchase",purchase).param("sku",sku).param("quantity",quantity).update();
        if(jdbc.sql("SELECT reservation_id FROM reservation_identity WHERE reservation_id=:id").param("id",id).query(UUID.class).optional().isEmpty())
            throw new org.springframework.dao.DataIntegrityViolationException("Purchase already bound to another reservation identity");
        jdbc.sql("""
            INSERT INTO reservation_request(reservation_id,purchase_id,sku,quantity,outcome,error_code)
            VALUES (:id,:purchase,:sku,:quantity,'REJECTED',:error) ON CONFLICT DO NOTHING
            """).param("id",id).param("purchase",purchase).param("sku",sku).param("quantity",quantity).param("error",error).update();
        var row=request(id).orElseThrow();
        if(!row.purchaseId().equals(purchase)||!row.sku().equals(sku)||row.quantity()!=quantity)throw new RequestConflict();
        // Replays preserve the committed decision; never recompute using today's stock/catalog.
        return row;
    }
    public Optional<Reservation> find(UUID id) {
        return jdbc.sql("SELECT * FROM reservation WHERE reservation_id=:id").param("id",id).query((r,n)->new Reservation(
            r.getObject("reservation_id",UUID.class),r.getObject("purchase_id",UUID.class),r.getString("sku"),r.getInt("quantity"),
            r.getLong("unit_price_minor"),r.getLong("total_amount_minor"),r.getString("currency"),Reservation.State.valueOf(r.getString("state")),
            r.getObject("created_at",OffsetDateTime.class).toInstant(),r.getObject("expires_at",OffsetDateTime.class).toInstant())).optional();
    }
}
