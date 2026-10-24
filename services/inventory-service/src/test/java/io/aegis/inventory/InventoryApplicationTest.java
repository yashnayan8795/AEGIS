package io.aegis.inventory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@ActiveProfiles("local")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventoryApplicationTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11")
            .withDatabaseName("aegis_inventory");
    @Autowired WebServerApplicationContext context;
    @Autowired ObjectMapper mapper;
    @Autowired DataSource dataSource;
    @Autowired Flyway flyway;

    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) throws SQLException {
        // Test-only provisioning. Migrations use owner credentials; the app gets SELECT only.
        try (var connection = owner(); var sql = connection.createStatement()) {
            sql.execute("CREATE ROLE inventory_reader LOGIN PASSWORD 'test-only-password'");
            sql.execute("REVOKE ALL ON DATABASE aegis_inventory FROM PUBLIC");
            sql.execute("GRANT CONNECT ON DATABASE aegis_inventory TO inventory_reader");
            sql.execute("REVOKE ALL ON SCHEMA public FROM PUBLIC");
            sql.execute("GRANT USAGE ON SCHEMA public TO inventory_reader");
            sql.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO inventory_reader");
            sql.execute("CREATE DATABASE other_service");
            sql.execute("REVOKE ALL ON DATABASE other_service FROM PUBLIC");
        }
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", () -> "inventory_reader");
        properties.add("spring.datasource.password", () -> "test-only-password");
        properties.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        properties.add("spring.flyway.user", POSTGRES::getUsername);
        properties.add("spring.flyway.password", POSTGRES::getPassword);
    }
    private static Connection owner() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
    private HttpResponse<String> get(String sku) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + context.getWebServer().getPort() + "/api/v1/items/" + sku))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void startsAndReadsKnownSkuFromPostgres() throws Exception {
        var response = get("AEGIS_ITEM");
        assertThat(response.statusCode()).isEqualTo(200);
        var body = mapper.readTree(response.body());
        assertThat(body.properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("sku", "unitPriceMinor", "currency", "availableQuantity", "observedAt");
        assertThat(body.get("sku").asText()).isEqualTo("AEGIS_ITEM");
        assertThat(body.get("unitPriceMinor").asLong()).isEqualTo(100);
        assertThat(body.get("currency").asText()).isEqualTo("INR");
        assertThat(body.get("availableQuantity").asLong()).isEqualTo(10);
        assertThat(Instant.parse(body.get("observedAt").asText())).isBeforeOrEqualTo(Instant.now());
    }
    @Test void emptyStockIsValid() throws Exception {
        assertThat(mapper.readTree(get("AEGIS_EMPTY").body()).get("availableQuantity").asLong()).isZero();
    }
    @Test void unknownSkuReturns404() throws Exception {
        var response = get("UNKNOWN");
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(mapper.readTree(response.body()).get("code").asText()).isEqualTo("ITEM_NOT_FOUND");
    }
    @Test void invalidSkuReturns400() throws Exception {
        var response = get("lowercase");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(mapper.readTree(response.body()).get("code").asText()).isEqualTo("INVALID_REQUEST");
    }
    @ParameterizedTest
    @ValueSource(strings = {
        "INSERT INTO catalog_item VALUES ('BAD_PRICE',0,'INR',1)",
        "INSERT INTO catalog_item VALUES ('BAD_PRICE',-1,'INR',1)",
        "INSERT INTO catalog_item VALUES ('BAD_STOCK',100,'INR',-1)",
        "INSERT INTO catalog_item VALUES ('BAD_CURRENCY',100,'USD',1)",
        "INSERT INTO catalog_item VALUES ('lowercase',100,'INR',1)",
        "INSERT INTO catalog_item VALUES ('AEGIS_ITEM',100,'INR',1)",
        "INSERT INTO catalog_item VALUES ('NULL_PRICE',NULL,'INR',1)",
        "INSERT INTO catalog_item VALUES ('NULL_STOCK',100,'INR',NULL)"
    })
    void databaseRejectsInvalidRows(String sql) throws Exception {
        try (var connection = owner(); var statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.execute(sql)).isInstanceOf(SQLException.class)
                .satisfies(e -> assertThat(((SQLException)e).getSQLState()).startsWith("23"));
        }
    }
    @Test void migrationsApplyAndValidate() {
        flyway.validate();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().applied()).hasSize(3); // Two schema versions plus opt-in synthetic fixture.
    }
    @Test void runtimeCannotWriteOrCreateObjects() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.execute("CREATE TABLE forbidden(id bigint)"))
                .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.execute("UPDATE catalog_item SET available_quantity=0"))
                .isInstanceOf(SQLException.class);
        }
    }
    @Test void runtimeCannotConnectToAnotherServiceDatabase() {
        String otherUrl = POSTGRES.getJdbcUrl().replace("/aegis_inventory", "/other_service");
        assertThatThrownBy(() -> {
            try (var ignored = DriverManager.getConnection(otherUrl, "inventory_reader", "test-only-password")) {
                throw new AssertionError("Unexpected cross-database access");
            }
        }).isInstanceOf(SQLException.class).satisfies(e -> assertThat(((SQLException)e).getSQLState()).isEqualTo("42501"));
    }
}
