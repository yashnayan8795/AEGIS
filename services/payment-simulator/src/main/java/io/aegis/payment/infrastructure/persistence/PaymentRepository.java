package io.aegis.payment.infrastructure.persistence;

import io.aegis.payment.domain.Payment;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentRepository {
    private final JdbcClient jdbc;
    public PaymentRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Optional<Payment> findById(UUID id) {
        return jdbc.sql("""
                SELECT payment_id, purchase_id, reservation_id, amount_minor, currency,
                       state, decline_code, created_at, updated_at
                FROM payment WHERE payment_id = :id
                """).param("id", id).query((rs, row) -> new Payment(
                        rs.getObject("payment_id", UUID.class), rs.getObject("purchase_id", UUID.class),
                        rs.getObject("reservation_id", UUID.class), rs.getLong("amount_minor"),
                        rs.getString("currency"), Payment.State.valueOf(rs.getString("state")),
                        rs.getString("decline_code"), rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("updated_at", OffsetDateTime.class).toInstant())).optional();
    }
}
