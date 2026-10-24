package io.aegis.inventory;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
class ReservationUnavailableTest {
 @Test void databaseLossReturns503WithoutInventingMutationOutcome()throws Exception{
  try(var db=new PostgreSQLContainer("postgres:17.11");var client=HttpClient.newHttpClient()){
   db.start();try(var app=new SpringApplicationBuilder(InventoryApplication.class).run("--server.port=0","--spring.main.banner-mode=off","--logging.level.root=WARN","--spring.datasource.url="+db.getJdbcUrl()+"&connectTimeout=1&socketTimeout=1","--spring.datasource.username="+db.getUsername(),"--spring.datasource.password="+db.getPassword(),"--spring.datasource.hikari.connection-timeout=1000","--spring.datasource.hikari.validation-timeout=1000","--spring.flyway.user="+db.getUsername(),"--spring.flyway.password="+db.getPassword())){
    String url="http://localhost:"+((WebServerApplicationContext)app).getWebServer().getPort()+"/api/v1/reservations/"+UUID.randomUUID();db.stop();
    for(String suffix:new String[]{"","/commit","/release"}){
     var b=HttpRequest.newBuilder(URI.create(url+suffix)).timeout(Duration.ofSeconds(15));if(suffix.isEmpty())b.GET();else b.PUT(HttpRequest.BodyPublishers.noBody());var r=client.send(b.build(),HttpResponse.BodyHandlers.ofString());assertThat(r.statusCode()).as(r.body()).isEqualTo(503);assertThat(JsonMapper.builder().build().readTree(r.body()).get("code").asText()).isEqualTo("SERVICE_UNAVAILABLE");assertThat(r.body()).doesNotContain("jdbc:","password","SELECT ");
    }
    var r=client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).header("Content-Type","application/json").PUT(HttpRequest.BodyPublishers.ofString("{\"purchaseId\":\""+UUID.randomUUID()+"\",\"sku\":\"AEGIS_ITEM\",\"quantity\":1}")).build(),HttpResponse.BodyHandlers.ofString());assertThat(r.statusCode()).as(r.body()).isEqualTo(503);assertThat(r.body()).contains("SERVICE_UNAVAILABLE").doesNotContain("jdbc:","password");
   }
  }
 }
}
