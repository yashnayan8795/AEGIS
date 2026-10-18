package io.aegis.payment;

import java.net.URI;
import java.net.http.*;
import java.sql.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentApplicationTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11").withDatabaseName("aegis_payment");
    @Autowired WebServerApplicationContext context;
    @Autowired ObjectMapper mapper;
    @Autowired Flyway flyway;
    @Autowired DataSource dataSource;
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) throws SQLException {
        try(var c=owner();var s=c.createStatement()) {
            s.execute("CREATE ROLE payment_reader LOGIN PASSWORD 'test-only-password'");
            s.execute("REVOKE ALL ON DATABASE aegis_payment FROM PUBLIC");
            s.execute("GRANT CONNECT ON DATABASE aegis_payment TO payment_reader");
            s.execute("REVOKE ALL ON SCHEMA public FROM PUBLIC");
            s.execute("GRANT USAGE ON SCHEMA public TO payment_reader");
            s.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO payment_reader");
            s.execute("CREATE DATABASE other_service");s.execute("REVOKE ALL ON DATABASE other_service FROM PUBLIC");
        }
        properties.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username",()->"payment_reader");properties.add("spring.datasource.password",()->"test-only-password");
        properties.add("spring.flyway.url",POSTGRES::getJdbcUrl);properties.add("spring.flyway.user",POSTGRES::getUsername);properties.add("spring.flyway.password",POSTGRES::getPassword);
    }
    static Connection owner()throws SQLException {return DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());}
    @BeforeEach void fixtures()throws SQLException {try(var c=owner()){ScriptUtils.executeSqlScript(c,new ClassPathResource("fixtures/payments.sql"));}}
    HttpResponse<String> get(String id)throws Exception {return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:"+context.getWebServer().getPort()+"/api/v1/payments/"+id)).GET().build(),HttpResponse.BodyHandlers.ofString());}
    @ParameterizedTest @ValueSource(strings={"11111111-1111-4111-8111-111111111111","22222222-2222-4222-8222-222222222222","33333333-3333-4333-8333-333333333333"})
    void readsStoredStateAndExactWireFields(String id)throws Exception {
        var response=get(id);assertThat(response.statusCode()).isEqualTo(200);var body=mapper.readTree(response.body());
        var schema=mapper.readTree(java.nio.file.Path.of("../../contracts/payment-v1.json").toFile()).get("components").get("schemas").get("Payment");
        var required=new java.util.ArrayList<String>();
        for(int i=0;i<schema.get("required").size();i++)required.add(schema.get("required").get(i).asText());
        assertThat(body.properties()).extracting(java.util.Map.Entry::getKey).containsExactlyInAnyOrderElementsOf(required);
        String expected=id.startsWith("1")?"PENDING":id.startsWith("2")?"SUCCEEDED":"DECLINED";
        assertThat(body.get("state").asText()).isEqualTo(expected);assertThat(body.get("currency").asText()).isEqualTo("INR");
        assertThat(body.get("amountMinor").asLong()).isEqualTo(id.startsWith("2")?Long.MAX_VALUE:200);
        if(expected.equals("DECLINED"))assertThat(body.get("declineCode").asText()).isEqualTo("SIMULATED_DECLINE");
        else assertThat(body.get("declineCode").isNull()).isTrue();
    }
    @Test void unknownIs404NotAFabricatedDecline()throws Exception {
        var r=get("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");assertThat(r.statusCode()).isEqualTo(404);assertThat(mapper.readTree(r.body()).get("code").asText()).isEqualTo("PAYMENT_NOT_FOUND");
    }
    @ParameterizedTest @ValueSource(strings={"bad","1-1-1-1-1"})
    void invalidUuidReturns400(String id)throws Exception {assertThat(get(id).statusCode()).isEqualTo(400);}
    @ParameterizedTest @ValueSource(strings={
        "amount_minor=0", "amount_minor=-1", "currency='USD'", "state='UNKNOWN'", "state='DECLINED',decline_code=NULL",
        "state='PENDING',decline_code='SIMULATED_DECLINE'", "state='DECLINED',decline_code='OTHER'", "updated_at=created_at-interval '1 second'", "purchase_id=NULL", "reservation_id=NULL"
    })
    void databaseRejectsInvalidValues(String assignment)throws SQLException {
        try(var c=owner();var s=c.createStatement()) {assertThatThrownBy(()->s.execute("UPDATE payment SET "+assignment+" WHERE payment_id='11111111-1111-4111-8111-111111111111'"))
            .isInstanceOf(SQLException.class).satisfies(e->assertThat(((SQLException)e).getSQLState()).startsWith("23"));}
    }
    @Test void uniquePurchaseAndPaymentIdentities()throws SQLException {
        try(var c=owner();var s=c.createStatement()) {
            assertThatThrownBy(()->s.execute("UPDATE payment SET purchase_id='22222222-2222-4222-8222-000000000001' WHERE payment_id='11111111-1111-4111-8111-111111111111'")) .isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.execute("INSERT INTO payment SELECT * FROM payment")) .isInstanceOf(SQLException.class);
        }
    }
    @Test void defaultMigrationsContainNoOutcomeSeeds()throws Exception {
        try(var c=owner();var s=c.createStatement()) {s.execute("TRUNCATE payment");}
        flyway.validate();assertThat(flyway.info().pending()).isEmpty();assertThat(flyway.info().applied()).hasSize(1);
        try(var c=dataSource.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM payment")){r.next();assertThat(r.getLong(1)).isZero();}
    }
    @Test void runtimeIsReadOnlyAndCannotUseAnotherDatabase()throws SQLException {
        try(var c=dataSource.getConnection();var s=c.createStatement()) {
            assertThatThrownBy(()->s.execute("UPDATE payment SET state='DECLINED'")) .isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.execute("CREATE TABLE forbidden(id int)")) .isInstanceOf(SQLException.class);
        }
        assertThatThrownBy(()->{try(var ignored=DriverManager.getConnection(POSTGRES.getJdbcUrl().replace("/aegis_payment","/other_service"),"payment_reader","test-only-password")){throw new AssertionError("Cross-database access");}})
            .isInstanceOf(SQLException.class).satisfies(e->assertThat(((SQLException)e).getSQLState()).isEqualTo("42501"));
    }
}
