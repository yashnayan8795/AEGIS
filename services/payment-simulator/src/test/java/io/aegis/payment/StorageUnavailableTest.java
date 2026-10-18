package io.aegis.payment;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class StorageUnavailableTest {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17.11").withDatabaseName("aegis_payment");
    @Test void databaseLossIs503NotAFabricatedOutcome()throws Exception {
        String url=POSTGRES.getJdbcUrl()+"&connectTimeout=1&socketTimeout=1";
        // This isolated outage fixture uses owner credentials; restricted-role tests are separate.
        try(var app=new SpringApplicationBuilder(PaymentApplication.class).run(
                "--server.port=0","--spring.main.banner-mode=off","--logging.level.root=WARN",
                "--spring.datasource.url="+url,"--spring.datasource.username="+POSTGRES.getUsername(),"--spring.datasource.password="+POSTGRES.getPassword(),
                "--spring.datasource.hikari.connection-timeout=1000","--spring.datasource.hikari.validation-timeout=500",
                "--spring.flyway.url="+url,"--spring.flyway.user="+POSTGRES.getUsername(),"--spring.flyway.password="+POSTGRES.getPassword());
            var client=HttpClient.newHttpClient()) {
            String id=UUID.randomUUID().toString();
            String origin="http://localhost:"+((WebServerApplicationContext)app).getWebServer().getPort()+"/api/v1/payments";
            var uri=URI.create(origin+"/"+id);
            var before=client.send(HttpRequest.newBuilder(uri).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertThat(before.statusCode()).isEqualTo(404);
            POSTGRES.stop();
            var response=client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(503);
            assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
            assertThat(response.body()).contains("SERVICE_UNAVAILABLE").doesNotContain("PSQLException", "jdbc:postgresql", "test-only");
            
        }
    }
}
