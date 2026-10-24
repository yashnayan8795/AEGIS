package io.aegis.checkout;

import java.net.URI;
import java.net.http.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.*;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class CheckoutApplicationTest {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17.11").withDatabaseName("aegis_checkout");
    @Autowired WebServerApplicationContext context;
    @Autowired ObjectMapper mapper;
    @Autowired Flyway flyway;
    @Autowired DataSource dataSource;
    @Autowired RequestMappingHandlerMapping mappings;
    static final String ID="00000000-0000-0000-0000-000000000001";

    @DynamicPropertySource static void database(DynamicPropertyRegistry properties)throws SQLException {
        // Disposable test provisioning only; application migrations do not create roles/databases.
        var migration=Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()).load();
        migration.migrate(); migration.validate();
        try(var c=owner();var s=c.createStatement()) {
            try(var r=s.executeQuery("SELECT count(*) FROM purchase")){r.next();assertThat(r.getLong(1)).isZero();}
            s.execute("CREATE ROLE checkout_reader LOGIN PASSWORD 'test-only-password'");
            s.execute("REVOKE ALL ON DATABASE aegis_checkout FROM PUBLIC");
            s.execute("GRANT CONNECT ON DATABASE aegis_checkout TO checkout_reader");
            s.execute("REVOKE ALL ON SCHEMA public FROM PUBLIC");s.execute("GRANT USAGE ON SCHEMA public TO checkout_reader");
            s.execute("GRANT SELECT ON purchase TO checkout_reader");
            s.execute("CREATE DATABASE other_service");s.execute("REVOKE ALL ON DATABASE other_service FROM PUBLIC");
        }
        properties.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username",()->"checkout_reader");properties.add("spring.datasource.password",()->"test-only-password");
        properties.add("spring.flyway.url",POSTGRES::getJdbcUrl);properties.add("spring.flyway.user",POSTGRES::getUsername);properties.add("spring.flyway.password",POSTGRES::getPassword);
    }
    static Connection owner()throws SQLException {return DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());}
    JsonNode fixtures()throws Exception {
        try(var input=new ClassPathResource("fixtures/purchases.json").getInputStream()){return mapper.readTree(input);}
    }
    @BeforeEach void reset()throws Exception {
        try(var c=owner();var s=c.createStatement()) {s.execute("TRUNCATE purchase CASCADE");for(var value:fixtures())insert(c,value);}
    }
    // Hypothetical representation fixtures only: no actual inventory/payment evidence or workflow execution.
    void insert(Connection c,JsonNode value)throws SQLException {
        try(var s=c.prepareStatement("""
            INSERT INTO purchase(purchase_id,sku,quantity,amount_minor,currency,state,step,
                compensation_pending,payment_outcome_known,created_at,updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)
            """)) {
            s.setObject(1,UUID.fromString(value.get("purchaseId").asString()));s.setString(2,value.get("sku").asString());
            s.setInt(3,value.get("quantity").asInt());
            if(value.get("amountMinor").isNull())s.setNull(4,Types.BIGINT);else s.setLong(4,value.get("amountMinor").asLong());
            s.setString(5,value.get("currency").isNull()?null:value.get("currency").asString());
            s.setString(6,value.get("state").asString());s.setString(7,value.get("step").asString());
            s.setBoolean(8,value.get("compensationPending").asBoolean());s.setBoolean(9,value.get("paymentOutcomeKnown").asBoolean());
            s.setObject(10,java.time.OffsetDateTime.parse(value.get("createdAt").asString()));
            s.setObject(11,java.time.OffsetDateTime.parse(value.get("updatedAt").asString()));s.executeUpdate();
        }
    }
    HttpResponse<String> get(String id)throws Exception {
        try(var client=HttpClient.newHttpClient()) {return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+context.getWebServer().getPort()+"/api/v1/purchases/"+id))
            .timeout(java.time.Duration.ofSeconds(10)).GET().build(),HttpResponse.BodyHandlers.ofString());}
    }
    @Test void allSixteenValidRepresentationsMatchContractAndRemainUnchanged()throws Exception {
        assertThat(context.getWebServer().getPort()).isPositive();assertThat(fixtures().size()).isEqualTo(16);
        var before=storedRows();
        for(var expected:fixtures()) {
            assertThat(PurchaseContract.matches(PurchaseContract.SCHEMA,expected)).isTrue();
            var response=get(expected.get("purchaseId").asString());assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/json");
            var body=mapper.readTree(response.body());assertThat(body).isEqualTo(expected);
            assertThat(PurchaseContract.matches(PurchaseContract.SCHEMA,body)).isTrue();
            assertThat(mapper.readTree(get(expected.get("purchaseId").asString()).body())).isEqualTo(expected);
        }
        // A separate owner connection confirms GET changed no persisted field, including timestamps.
        assertThat(storedRows()).isEqualTo(before);
    }
    List<String> storedRows()throws SQLException {
        var rows=new ArrayList<String>();
        try(var c=owner();var s=c.createStatement();var r=s.executeQuery("SELECT to_jsonb(purchase)::text FROM purchase ORDER BY purchase_id")) {
            while(r.next()) rows.add(r.getString(1));
        }
        return rows;
    }
    @Test void postgresAndContractAgreeOnAll400SnapshotCombinations()throws Exception {
        int accepted=0,rejected=0;var base=(tools.jackson.databind.node.ObjectNode)fixtures().get(0);
        try(var c=owner()) {
            for(var state:PurchaseContract.SCHEMA.path("properties").path("state").path("enum"))
            for(var step:PurchaseContract.SCHEMA.path("properties").path("step").path("enum"))
            for(boolean known:new boolean[]{false,true})for(boolean compensation:new boolean[]{false,true})for(boolean priced:new boolean[]{false,true})for(boolean currencyPresent:new boolean[]{false,true}) {
                var candidate=base.deepCopy();candidate.put("purchaseId",UUID.randomUUID().toString());
                candidate.set("state",state);candidate.set("step",step);candidate.put("paymentOutcomeKnown",known);candidate.put("compensationPending",compensation);
                if(priced)candidate.put("amountMinor",200);else candidate.putNull("amountMinor");
                if(currencyPresent)candidate.put("currency","INR");else candidate.putNull("currency");
                if(PurchaseContract.matches(PurchaseContract.SCHEMA,candidate)){insert(c,candidate);accepted++;}
                else {assertThatThrownBy(()->insert(c,candidate)).isInstanceOf(SQLException.class)
                    .satisfies(ex->assertThat(((SQLException)ex).getSQLState()).isEqualTo("23514"));rejected++;}
            }
        }
        assertThat(accepted).isEqualTo(16);assertThat(rejected).isEqualTo(384);
    }
    @Test void unknownHasContractProblemRatherThanInventedFailure()throws Exception {
        var response=get("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");assertProblem(response,404,"PURCHASE_NOT_FOUND");
    }
    @ParameterizedTest @ValueSource(strings={"bad","1-1-1-1-1","11111111111141118111111111111111"})
    void invalidUuidIsContract400(String id)throws Exception {assertProblem(get(id),400,"INVALID_REQUEST");}
    @Test void uuidCaseIsSemantic()throws Exception {
        var upper="AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA";
        var value=((tools.jackson.databind.node.ObjectNode)fixtures().get(0)).deepCopy();value.put("purchaseId",upper.toLowerCase(Locale.ROOT));
        try(var c=owner()){insert(c,value);}assertThat(get(upper).statusCode()).isEqualTo(200);
    }
    void assertProblem(HttpResponse<String> response,int status,String code) {
        assertThat(response.statusCode()).isEqualTo(status);assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        var body=mapper.readTree(response.body());assertThat(body.path("status").asInt()).isEqualTo(status);assertThat(body.path("code").asString()).isEqualTo(code);
        assertThat(PurchaseContract.matches(PurchaseContract.DOCUMENT.path("components").path("schemas").path("Problem"),body)).isTrue();
        assertThat(body.path("instance").asString()).startsWith("/api/v1/purchases/");
    }
    @ParameterizedTest @ValueSource(strings={
        "sku=''","sku='lowercase'","sku='_BAD'","sku='BAD SKU'","sku='AEGIS_ITEM'||chr(10)","sku=NULL","quantity=0","quantity=-1","quantity=NULL",
        "amount_minor=0,currency='INR'","amount_minor=-1,currency='INR'","amount_minor=200,currency=NULL","amount_minor=NULL,currency='INR'",
        "amount_minor=200,currency='USD'","state='UNKNOWN'","step='UNKNOWN'","state=NULL","step=NULL",
        "compensation_pending=NULL","payment_outcome_known=NULL","updated_at=created_at-interval '1 microsecond'","created_at=NULL","updated_at=NULL","updated_at='infinity'","created_at='-infinity'"
    })
    void rejectsInvalidStaticRows(String assignment)throws SQLException {
        try(var c=owner();var s=c.createStatement()) {
            assertThatThrownBy(()->s.execute("UPDATE purchase SET "+assignment+" WHERE purchase_id='"+ID+"'"))
                .isInstanceOf(SQLException.class).satisfies(ex->assertThat(((SQLException)ex).getSQLState()).startsWith("23"));
        }
    }
    @Test void rejectsOutOfRangeAndOversizedValues()throws SQLException {
        try(var c=owner();var s=c.createStatement()) {
            for(String assignment:List.of("sku=repeat('A',65)","quantity=2147483648","amount_minor=9223372036854775808,currency='INR'"))
                assertThatThrownBy(()->s.execute("UPDATE purchase SET "+assignment+" WHERE purchase_id='"+ID+"'"))
                    .isInstanceOf(SQLException.class).satisfies(ex->assertThat(((SQLException)ex).getSQLState()).startsWith("22"));
        }
    }
    @Test void identityIsUnique()throws SQLException {
        try(var c=owner();var s=c.createStatement()) {
            assertThatThrownBy(()->s.execute("INSERT INTO purchase SELECT * FROM purchase WHERE purchase_id='"+ID+"'"))
                .isInstanceOf(SQLException.class).satisfies(ex->assertThat(((SQLException)ex).getSQLState()).isEqualTo("23505"));
        }
    }
    @Test void utcTimestampsAndEqualTimestampBoundary()throws Exception {
        try(var c=owner();var s=c.createStatement()){s.execute("UPDATE purchase SET created_at='2026-10-17T05:30:00+05:30',updated_at='2026-10-17T05:30:00+05:30' WHERE purchase_id='"+ID+"'");}
        var body=mapper.readTree(get(ID).body());assertThat(Instant.parse(body.get("createdAt").asString())).isEqualTo(Instant.parse("2026-10-17T00:00:00Z"));
        assertThat(body.get("updatedAt")).isEqualTo(body.get("createdAt"));
    }
    @Test void migrationsValidateAndDoNotReinsertFixtures()throws SQLException {
        try(var c=owner();var s=c.createStatement()){s.execute("TRUNCATE purchase CASCADE");}
        flyway.migrate();flyway.validate();assertThat(flyway.info().pending()).isEmpty();assertThat(flyway.info().applied()).hasSize(2);
        try(var c=dataSource.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM purchase")){r.next();assertThat(r.getLong(1)).isZero();}
    }
    @ParameterizedTest @ValueSource(strings={"INSERT INTO purchase SELECT * FROM purchase","UPDATE purchase SET quantity=1","DELETE FROM purchase","TRUNCATE purchase CASCADE","CREATE TABLE forbidden(id int)","ALTER TABLE purchase ADD COLUMN forbidden int","SELECT * FROM flyway_schema_history"})
    void runtimeHasOnlyReadPrivilege(String sql)throws SQLException {
        try(var c=dataSource.getConnection();var s=c.createStatement()) {
            assertThatThrownBy(()->s.execute(sql)).isInstanceOf(SQLException.class)
                .satisfies(ex->assertThat(((SQLException)ex).getSQLState()).isEqualTo("42501"));
        }
    }
    @Test void runtimeCannotConnectToSiblingDatabase() {
        assertThatThrownBy(()->{try(var ignored=DriverManager.getConnection(POSTGRES.getJdbcUrl().replace("/aegis_checkout","/other_service"),"checkout_reader","test-only-password")){throw new AssertionError("Cross-database access");}})
            .isInstanceOf(SQLException.class).satisfies(ex->assertThat(((SQLException)ex).getSQLState()).isEqualTo("42501"));
    }
    @Test void onlyPurchaseGetBusinessHandlerIsRegistered() {
        var business=mappings.getHandlerMethods().entrySet().stream().filter(e->e.getValue().getBeanType().getPackageName().startsWith("io.aegis.checkout.api")).toList();
        assertThat(business).hasSize(1);assertThat(business.getFirst().getKey().getPatternValues()).containsExactly("/api/v1/purchases/{purchaseId}");
        assertThat(business.getFirst().getKey().getMethodsCondition().getMethods()).containsExactly(RequestMethod.GET);
    }
}
