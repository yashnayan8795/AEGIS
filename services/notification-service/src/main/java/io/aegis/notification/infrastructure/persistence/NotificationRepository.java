package io.aegis.notification.infrastructure.persistence;

import io.aegis.notification.domain.NotificationReceipt;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepository {
    private final JdbcClient jdbc;
    public NotificationRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Optional<NotificationReceipt> insertIfAbsent(UUID id, UUID purchaseId, String kind) {
        return jdbc.sql("""
                INSERT INTO notification_receipt (notification_id, purchase_id, kind)
                VALUES (:id, :purchase, :kind)
                ON CONFLICT (notification_id) DO NOTHING
                RETURNING notification_id, purchase_id, kind, state, created_at
                """).param("id", id).param("purchase", purchaseId).param("kind", kind)
                .query(NotificationRepository::map).optional();
    }
    public Optional<NotificationReceipt> findById(UUID id) {
        return jdbc.sql("""
                SELECT notification_id, purchase_id, kind, state, created_at
                FROM notification_receipt WHERE notification_id = :id
                """).param("id", id).query(NotificationRepository::map).optional();
    }
    private static NotificationReceipt map(ResultSet rs, int row) throws SQLException {
        return new NotificationReceipt(rs.getObject("notification_id", UUID.class),
                rs.getObject("purchase_id", UUID.class), rs.getString("kind"), rs.getString("state"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
