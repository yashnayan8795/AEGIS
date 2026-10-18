package io.aegis.notification;

import java.net.URI;
import java.net.http.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationApplicationTest {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17.11").withDatabaseName("aegis_notification");
    ConfigurableApplicationContext first,second;
    final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    final ObjectMapper mapper=JsonMapper.builder().build();
    @BeforeAll void instances()throws SQLException {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()).load().migrate();
        try(var c=owner();var s=c.createStatement()) {
            s.execute("CREATE ROLE notification_writer LOGIN PASSWORD 'test-only-password'");
            s.execute("REVOKE ALL ON DATABASE aegis_notification FROM PUBLIC");s.execute("GRANT CONNECT ON DATABASE aegis_notification TO notification_writer");
            s.execute("REVOKE ALL ON SCHEMA public FROM PUBLIC");s.execute("GRANT USAGE ON SCHEMA public TO notification_writer");
            s.execute("GRANT SELECT ON notification_receipt TO notification_writer");
            s.execute("GRANT INSERT (notification_id,purchase_id,kind) ON notification_receipt TO notification_writer");
            s.execute("CREATE DATABASE other_service");s.execute("REVOKE ALL ON DATABASE other_service FROM PUBLIC");
        }
        first=start();second=start();
    }
    ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(NotificationApplication.class).run(
            "--server.port=0","--spring.main.banner-mode=off","--logging.level.root=WARN",
            "--spring.datasource.url="+POSTGRES.getJdbcUrl(),"--spring.datasource.username=notification_writer","--spring.datasource.password=test-only-password",
            "--spring.flyway.url="+POSTGRES.getJdbcUrl(),"--spring.flyway.user="+POSTGRES.getUsername(),"--spring.flyway.password="+POSTGRES.getPassword());
    }
    @AfterAll void close() {if(first!=null)first.close();if(second!=null)second.close();client.close();}
    @BeforeEach void clean()throws SQLException {try(var c=owner();var s=c.createStatement()){s.execute("TRUNCATE notification_receipt");}}
    static Connection owner()throws SQLException {return DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());}
    String url(ConfigurableApplicationContext app){return "http://localhost:"+((WebServerApplicationContext)app).getWebServer().getPort()+"/api/v1/notifications";}
    String body(UUID id,UUID purchase){return "{\"notificationId\":\""+id+"\",\"purchaseId\":\""+purchase+"\",\"kind\":\"PURCHASE_CONFIRMED\"}";}
    HttpResponse<String> post(ConfigurableApplicationContext app,String body)throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url(app))).timeout(Duration.ofSeconds(15)).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> get(ConfigurableApplicationContext app,String id)throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url(app)+"/"+id)).timeout(Duration.ofSeconds(15)).GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    long count()throws SQLException {try(var c=owner();var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM notification_receipt")){r.next();return r.getLong(1);}}
    @Test void createReplayConflictAndTimestampAreDurable()throws Exception {
        var id=UUID.randomUUID();var purchase=UUID.randomUUID();var created=post(first,body(id,purchase));
        assertThat(created.statusCode()).isEqualTo(201);assertThat(created.headers().firstValue("Location")).contains("/api/v1/notifications/"+id);
        assertThat(count()).isEqualTo(1); // Independent connection sees commit before success is consumed.
        var node=mapper.readTree(created.body());var schema=mapper.readTree(java.nio.file.Path.of("../../contracts/notification-v1.json").toFile()).get("components").get("schemas").get("NotificationReceipt");
        var required=new ArrayList<String>();for(int i=0;i<schema.get("required").size();i++)required.add(schema.get("required").get(i).asText());
        assertThat(node.properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrderElementsOf(required);
        assertThat(node.get("state").asText()).isEqualTo("STORED");
        String formatted="{ \"kind\": \"PURCHASE_CONFIRMED\", \"purchaseId\": \""+purchase.toString().toUpperCase(Locale.ROOT)+"\", \"notificationId\": \""+id.toString().toUpperCase(Locale.ROOT)+"\" }";
        var replay=post(second,formatted);assertThat(replay.statusCode()).isEqualTo(200);assertThat(mapper.readTree(replay.body())).isEqualTo(node);
        var conflict=post(second,body(id,UUID.randomUUID()));assertThat(conflict.statusCode()).isEqualTo(409);assertThat(mapper.readTree(conflict.body()).get("code").asText()).isEqualTo("NOTIFICATION_ID_REUSED");
        assertThat(mapper.readTree(get(first,id.toString()).body())).isEqualTo(node);assertThat(count()).isEqualTo(1);
        assertThat(post(first,body(id,purchase)).statusCode()).isEqualTo(200);
    }
    @Test void missingReceiptIs404()throws Exception {var r=get(first,UUID.randomUUID().toString());assertThat(r.statusCode()).isEqualTo(404);assertThat(mapper.readTree(r.body()).get("code").asText()).isEqualTo("NOTIFICATION_NOT_FOUND");}
    @Test void invalidUuidIs400()throws Exception {
        assertThat(get(first,"1-1-1-1-1").statusCode()).isEqualTo(400);
        var r=post(first,"{\"notificationId\":\"bad\",\"purchaseId\":\""+UUID.randomUUID()+"\",\"kind\":\"PURCHASE_CONFIRMED\"}");assertThat(r.statusCode()).isEqualTo(400);assertThat(count()).isZero();
    }
    @Test void invalidKindNeverInserts()throws Exception {var r=post(first,body(UUID.randomUUID(),UUID.randomUUID()).replace("PURCHASE_CONFIRMED","EMAIL_SENT"));assertThat(r.statusCode()).isEqualTo(400);assertThat(count()).isZero();}
    @ParameterizedTest @ValueSource(booleans={false,true})
    void twoInstancesResolveConcurrentRequests(boolean conflicting)throws Exception {
        int workers=8;var id=UUID.randomUUID();var common=UUID.randomUUID();var ready=new CountDownLatch(workers);var go=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(workers)) {
            var futures=new ArrayList<Future<HttpResponse<String>>>();
            for(int i=0;i<workers;i++){final int index=i;var purchase=conflicting?UUID.randomUUID():common;
                futures.add(pool.submit(()->{ready.countDown();if(!go.await(10,TimeUnit.SECONDS))throw new AssertionError("Start gate timeout");return post(index%2==0?first:second,body(id,purchase));}));}
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();go.countDown();var responses=new ArrayList<HttpResponse<String>>();
            for(var f:futures)responses.add(f.get(20,TimeUnit.SECONDS));
            assertThat(responses.stream().filter(r->r.statusCode()==201).count()).isEqualTo(1);
            assertThat(responses.stream().filter(r->r.statusCode()==(conflicting?409:200)).count()).isEqualTo(workers-1);
            var winner=mapper.readTree(responses.stream().filter(r->r.statusCode()==201).findFirst().orElseThrow().body());
            assertThat(mapper.readTree(get(second,id.toString()).body())).isEqualTo(winner);assertThat(count()).isEqualTo(1);
            if(!conflicting)for(var r:responses)assertThat(mapper.readTree(r.body())).isEqualTo(winner);
        } finally {go.countDown();}
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void uncommittedCompetitorIsResolvedAfterCommitOrRollback(boolean commit)throws Exception {
        var id=UUID.randomUUID();var purchase=UUID.randomUUID();
        try(var c=owner();var pool=Executors.newSingleThreadExecutor()) {
            c.setAutoCommit(false);
            try(var s=c.prepareStatement("INSERT INTO notification_receipt(notification_id,purchase_id,kind) VALUES (?,?,'PURCHASE_CONFIRMED')")){s.setObject(1,id);s.setObject(2,purchase);s.executeUpdate();}
            var response=pool.submit(()->post(second,body(id,purchase)));
            try {
                await().atMost(Duration.ofSeconds(10)).until(()->{
                    try(var observer=owner();var q=observer.createStatement();var r=q.executeQuery("SELECT count(*) FROM pg_stat_activity WHERE usename='notification_writer' AND wait_event_type='Lock' AND query LIKE '%INSERT INTO notification_receipt%'")){r.next();return r.getLong(1)>0;}
                });
                assertThat(response.isDone()).isFalse();
                if(commit)c.commit();else c.rollback();
                assertThat(response.get(15,TimeUnit.SECONDS).statusCode()).isEqualTo(commit?200:201);assertThat(count()).isEqualTo(1);
            } finally {c.rollback();}
        }
    }
    @Test void applicationRestartKeepsReceipt()throws Exception {
        var id=UUID.randomUUID();var purchase=UUID.randomUUID();var original=post(first,body(id,purchase));assertThat(original.statusCode()).isEqualTo(201);
        first.close();first=start();var retrieved=get(first,id.toString());assertThat(retrieved.statusCode()).isEqualTo(200);assertThat(mapper.readTree(retrieved.body())).isEqualTo(mapper.readTree(original.body()));
        assertThat(post(first,body(id,purchase)).statusCode()).isEqualTo(200);assertThat(count()).isEqualTo(1);
    }
    @Test void migrationsAndRuntimePrivilegesAreRestricted()throws SQLException {
        first.getBean(Flyway.class).validate();assertThat(first.getBean(Flyway.class).info().applied()).hasSize(1);
        try(var c=DriverManager.getConnection(POSTGRES.getJdbcUrl(),"notification_writer","test-only-password");var s=c.createStatement()){
            assertThatThrownBy(()->s.execute("DELETE FROM notification_receipt")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.execute("UPDATE notification_receipt SET kind='PURCHASE_CONFIRMED'")) .isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.execute("CREATE TABLE forbidden(id int)")) .isInstanceOf(SQLException.class);
        }
        assertThatThrownBy(()->{try(var ignored=DriverManager.getConnection(POSTGRES.getJdbcUrl().replace("/aegis_notification","/other_service"),"notification_writer","test-only-password")){throw new AssertionError("Cross database access");}})
            .isInstanceOf(SQLException.class).satisfies(e->assertThat(((SQLException)e).getSQLState()).isEqualTo("42501"));
    }
    @ParameterizedTest @ValueSource(strings={"kind='EMAIL_SENT'","state='SENT'","purchase_id=NULL","created_at=NULL"})
    void databaseRejectsInvalidReceipt(String assignment)throws Exception {
        var id=UUID.randomUUID();post(first,body(id,UUID.randomUUID()));
        try(var c=owner();var s=c.createStatement()){assertThatThrownBy(()->s.execute("UPDATE notification_receipt SET "+assignment)) .isInstanceOf(SQLException.class).satisfies(e->assertThat(((SQLException)e).getSQLState()).startsWith("23"));}
    }
}
