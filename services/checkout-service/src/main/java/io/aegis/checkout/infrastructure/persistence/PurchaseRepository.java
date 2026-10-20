package io.aegis.checkout.infrastructure.persistence;

import io.aegis.checkout.domain.Purchase;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PurchaseRepository {
    private final JdbcClient jdbc;
    public PurchaseRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Optional<Purchase> findById(UUID id) {
        return jdbc.sql("""
                SELECT purchase_id, sku, quantity, amount_minor, currency, state, step,
                       compensation_pending, payment_outcome_known, created_at, updated_at
                FROM purchase WHERE purchase_id = :id
                """).param("id", id).query((rs, row) -> new Purchase(
                        rs.getObject("purchase_id", UUID.class), rs.getString("sku"), rs.getInt("quantity"),
                        rs.getObject("amount_minor", Long.class), rs.getString("currency"),
                        Purchase.State.valueOf(rs.getString("state")), Purchase.Step.valueOf(rs.getString("step")),
                        rs.getBoolean("compensation_pending"), rs.getBoolean("payment_outcome_known"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("updated_at", OffsetDateTime.class).toInstant())).optional();
    }
}
