package io.aegis.inventory.infrastructure.persistence;

import io.aegis.inventory.domain.CatalogItem;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {
    private final JdbcClient jdbc;
    public CatalogRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Optional<CatalogItem> findBySku(String sku) {
        return jdbc.sql("""
                SELECT sku, unit_price_minor, currency, available_quantity
                FROM catalog_item WHERE sku = :sku
                """)
                .param("sku", sku)
                .query((rs, row) -> new CatalogItem(rs.getString("sku"), rs.getLong("unit_price_minor"),
                        rs.getString("currency"), rs.getLong("available_quantity")))
                .optional();
    }
}
