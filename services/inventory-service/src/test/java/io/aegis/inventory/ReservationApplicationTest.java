package io.aegis.inventory;

import io.aegis.inventory.infrastructure.persistence.ReservationTransactions;
import java.net.URI;
import java.net.http.*;
import java.sql.*;
import java.time.*;
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
class ReservationApplicationTest {
 @Container static final PostgreSQLContainer DB=new PostgreSQLContainer("postgres:17.11").withDatabaseName("aegis_inventory");
 ConfigurableApplicationContext first,second;
 final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
 final ObjectMapper json=JsonMapper.builder().build();
 static Connection owner()throws SQLException{return DriverManager.getConnection(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword());}
 static void sql(String value)throws SQLException{try(var c=owner();var s=c.createStatement()){s.execute(value);}}
 @BeforeAll void start()throws Exception{
  Flyway.configure().dataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()).load().migrate();
  sql("CREATE ROLE inventory_writer LOGIN PASSWORD 'test-only-password'; REVOKE ALL ON DATABASE aegis_inventory FROM PUBLIC; GRANT CONNECT ON DATABASE aegis_inventory TO inventory_writer; REVOKE ALL ON SCHEMA public FROM PUBLIC; GRANT USAGE ON SCHEMA public TO inventory_writer; GRANT SELECT ON catalog_item,reservation_identity,reservation_request,reservation TO inventory_writer; GRANT INSERT ON reservation_identity,reservation_request,reservation TO inventory_writer; GRANT UPDATE(available_quantity,reserved_quantity,sold_quantity) ON catalog_item TO inventory_writer; GRANT UPDATE(state) ON reservation TO inventory_writer");
  sql("CREATE DATABASE other_service");sql("REVOKE ALL ON DATABASE other_service FROM PUBLIC");
  first=startInstance("30s");second=startInstance("2s");
 }
 ConfigurableApplicationContext startInstance(String ttl){return new SpringApplicationBuilder(InventoryApplication.class).run(
  "--server.port=0","--spring.main.banner-mode=off","--logging.level.root=WARN","--inventory.reservation.ttl="+ttl,
  "--spring.datasource.url="+DB.getJdbcUrl(),"--spring.datasource.username=inventory_writer","--spring.datasource.password=test-only-password",
  "--spring.flyway.url="+DB.getJdbcUrl(),"--spring.flyway.user="+DB.getUsername(),"--spring.flyway.password="+DB.getPassword());}
 @AfterAll void close(){if(first!=null)first.close();if(second!=null)second.close();http.close();}
 @BeforeEach void reset()throws Exception{sql("TRUNCATE catalog_item,reservation_identity,reservation_request,reservation CASCADE; INSERT INTO catalog_item(sku,unit_price_minor,currency,available_quantity) VALUES ('AEGIS_ITEM',125,'INR',10)");}
 String base(ConfigurableApplicationContext app){return "http://localhost:"+((WebServerApplicationContext)app).getWebServer().getPort()+"/api/v1/reservations/";}
 String payload(UUID purchase,int quantity){return "{\"purchaseId\":\""+purchase+"\",\"sku\":\"AEGIS_ITEM\",\"quantity\":"+quantity+"}";}
 HttpResponse<String> request(ConfigurableApplicationContext app,String id,String method,String body)throws Exception{
  var b=HttpRequest.newBuilder(URI.create(base(app)+id)).timeout(Duration.ofSeconds(20));
  if(body!=null)b.header("Content-Type","application/json");
  b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
  return http.send(b.build(),HttpResponse.BodyHandlers.ofString());
 }
 HttpResponse<String> reserve(ConfigurableApplicationContext app,UUID id,UUID purchase,int quantity)throws Exception{return request(app,id.toString(),"PUT",payload(purchase,quantity));}
 HttpResponse<String> command(ConfigurableApplicationContext app,UUID id,String action)throws Exception{return request(app,id+"/"+action,"PUT",null);}
 JsonNode node(HttpResponse<String> r){return json.readTree(r.body());}
 void error(HttpResponse<String> r,int status,String code){assertThat(r.statusCode()).as(r.body()).isEqualTo(status);assertThat(node(r).get("code").asText()).isEqualTo(code);assertThat(r.body()).doesNotContain("jdbc:","password","SELECT ","PSQLException");}
 long count(String table)throws SQLException{try(var c=owner();var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM "+table)){r.next();return r.getLong(1);}}
 long[] balances()throws SQLException{try(var c=owner();var s=c.createStatement();var r=s.executeQuery("SELECT available_quantity,reserved_quantity,sold_quantity,initial_quantity,(SELECT coalesce(sum(quantity),0) FROM reservation WHERE state='ACTIVE'),(SELECT coalesce(sum(quantity),0) FROM reservation WHERE state='COMMITTED') FROM catalog_item WHERE sku='AEGIS_ITEM'")){assertThat(r.next()).isTrue();long[] b=new long[6];for(int i=0;i<6;i++)b[i]=r.getLong(i+1);assertThat(b[0]).isNotNegative();assertThat(b[1]).isEqualTo(b[4]);assertThat(b[2]).isEqualTo(b[5]);assertThat(b[0]+b[1]+b[2]).isEqualTo(b[3]);return b;}}
 Instant deadline(UUID id)throws Exception{return Instant.parse(node(request(first,id.toString(),"GET",null)).get("expiresAt").asText());}
 void past(Instant deadline){await().pollInterval(Duration.ofMillis(20)).atMost(Duration.ofSeconds(10)).until(()->{try(var c=owner();var s=c.prepareStatement("SELECT clock_timestamp()>=?")){s.setObject(1,OffsetDateTime.ofInstant(deadline,ZoneOffset.UTC));try(var r=s.executeQuery()){r.next();return r.getBoolean(1);}}});}
 void waiting(int count,String fragment){await().pollInterval(Duration.ofMillis(20)).atMost(Duration.ofSeconds(10)).until(()->{try(var c=owner();var s=c.prepareStatement("SELECT count(*) FROM pg_stat_activity WHERE usename='inventory_writer' AND wait_event_type='Lock' AND query LIKE ?")){s.setString(1,"%"+fragment+"%");try(var r=s.executeQuery()){r.next();return r.getInt(1)>=count;}}});}
 List<HttpResponse<String>> race(Callable<HttpResponse<String>> a,Callable<HttpResponse<String>> b)throws Exception{
  var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
  try(var gate=owner();var pool=Executors.newFixedThreadPool(2)){
   gate.setAutoCommit(false);try(var statement=gate.createStatement()){statement.executeQuery("SELECT sku FROM catalog_item WHERE sku='AEGIS_ITEM' FOR UPDATE").close();}
   var f1=pool.submit(()->{ready.countDown();if(!go.await(10,TimeUnit.SECONDS))throw new AssertionError();return a.call();});
   var f2=pool.submit(()->{ready.countDown();if(!go.await(10,TimeUnit.SECONDS))throw new AssertionError();return b.call();});
   try{
    assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();go.countDown();
    // Both operations must be observed blocked in PostgreSQL, not merely submitted to an executor.
    waiting(2,"");gate.commit();return List.of(f1.get(25,TimeUnit.SECONDS),f2.get(25,TimeUnit.SECONDS));
   }finally{go.countDown();gate.rollback();}
  }
 }
 @Test void createReplayAndCommitPreserveAuthoritativeSnapshot()throws Exception{
  var id=UUID.randomUUID();var purchase=UUID.randomUUID();var created=reserve(first,id,purchase,2);
  assertThat(created.statusCode()).as(created.body()).isEqualTo(201);assertThat(created.headers().firstValue("Location")).contains("/api/v1/reservations/"+id);assertThat(count("reservation")).isEqualTo(1);
  var n=node(created);assertThat(n.get("unitPriceMinor").asLong()).isEqualTo(125);assertThat(n.get("totalAmountMinor").asLong()).isEqualTo(250);assertThat(n.get("currency").asText()).isEqualTo("INR");assertThat(balances()).startsWith(8,2,0,10);
  var schema=json.readTree(java.nio.file.Path.of("../../contracts/inventory-v1.json").toFile()).get("components").get("schemas").get("Reservation");var fields=new ArrayList<String>();schema.get("required").forEach(x->fields.add(x.asText()));assertThat(n.properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrderElementsOf(fields);
  sql("UPDATE catalog_item SET unit_price_minor=999");var replay=reserve(second,id,purchase,2);assertThat(replay.statusCode()).isEqualTo(200);assertThat(node(replay)).isEqualTo(n);
  var committed=command(first,id,"commit");assertThat(committed.statusCode()).as(committed.body()).isEqualTo(200);assertThat(node(committed).get("state").asText()).isEqualTo("COMMITTED");assertThat(balances()).startsWith(8,0,2,10);
  assertThat(node(command(second,id,"commit"))).isEqualTo(node(committed));error(command(second,id,"release"),409,"RESERVATION_COMMITTED");assertThat(node(reserve(second,id,purchase,2))).isEqualTo(node(committed));
 }
 @Test void routeInventoryContainsOnlyApprovedHandlers(){
  var mappings=first.getBean(org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class).getHandlerMethods();
  var paths=new java.util.HashSet<String>();mappings.forEach((mapping,handler)->{if(handler.getBeanType().getPackageName().startsWith("io.aegis.inventory"))for(var pattern:mapping.getPatternValues())for(var method:mapping.getMethodsCondition().getMethods())paths.add(method+" "+pattern);});
  assertThat(paths).containsExactlyInAnyOrder("GET /api/v1/items/{sku}","GET /api/v1/reservations/{reservationId}","PUT /api/v1/reservations/{reservationId}","PUT /api/v1/reservations/{reservationId}/commit","PUT /api/v1/reservations/{reservationId}/release");
 }
 @Test void exportActualContractSamples()throws Exception{
  var id=UUID.randomUUID();var p=UUID.randomUUID();var samples=new ArrayList<Map<String,Object>>();
  var created=reserve(first,id,p,2);samples.add(Map.of("schema","Reservation","body",node(created)));samples.add(Map.of("schema","Reservation","body",node(reserve(second,id,p,2))));
  samples.add(Map.of("schema","Reservation","body",node(command(first,id,"commit"))));
  for(var response:List.of(command(first,id,"release"),request(first,UUID.randomUUID().toString(),"GET",null),reserve(second,id,p,3)))samples.add(Map.of("schema","Problem","body",node(response)));
  java.nio.file.Files.writeString(java.nio.file.Path.of("target/reservation-contract-samples.json"),json.writeValueAsString(samples));
 }
 @Test void lastUnitContentionAcrossIndependentInstances()throws Exception{
  sql("TRUNCATE catalog_item CASCADE; INSERT INTO catalog_item(sku,unit_price_minor,currency,available_quantity) VALUES ('AEGIS_ITEM',125,'INR',1)");
  var results=race(()->reserve(first,UUID.randomUUID(),UUID.randomUUID(),1),()->reserve(second,UUID.randomUUID(),UUID.randomUUID(),1));assertThat(results).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(201,409);error(results.stream().filter(r->r.statusCode()==409).findFirst().orElseThrow(),409,"INSUFFICIENT_STOCK");assertThat(count("reservation")).isEqualTo(1);assertThat(count("reservation_request")).isEqualTo(2);assertThat(balances()).startsWith(0,1,0,1);
 }
 @ParameterizedTest @ValueSource(booleans={false,true})
 void duplicateAndConflictingIdentityRaces(boolean conflict)throws Exception{
  var id=UUID.randomUUID();var purchase=UUID.randomUUID();var results=race(()->reserve(first,id,purchase,2),()->reserve(second,id,purchase,conflict?3:2));
  assertThat(results).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(201,conflict?409:200);assertThat(count("reservation")).isEqualTo(1);assertThat(count("reservation_identity")).isEqualTo(1);
  if(conflict)error(results.stream().filter(r->r.statusCode()==409).findFirst().orElseThrow(),409,"RESERVATION_ID_REUSED");else assertThat(node(results.get(0))).isEqualTo(node(results.get(1)));balances();
 }
 @Test void purchaseCannotBindTwoReservationIdentities()throws Exception{var p=UUID.randomUUID();var results=race(()->reserve(first,UUID.randomUUID(),p,1),()->reserve(second,UUID.randomUUID(),p,1));assertThat(results).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(201,409);error(results.stream().filter(r->r.statusCode()==409).findFirst().orElseThrow(),409,"PURCHASE_RESERVATION_EXISTS");assertThat(count("reservation_identity")).isEqualTo(1);assertThat(balances()).startsWith(9,1,0,10);}
 @Test void commitVersusReleaseHasOneTerminalWinner()throws Exception{var id=UUID.randomUUID();assertThat(reserve(first,id,UUID.randomUUID(),2).statusCode()).isEqualTo(201);var results=race(()->command(first,id,"commit"),()->command(second,id,"release"));assertThat(results).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(200,409);var state=node(request(first,id.toString(),"GET",null)).get("state").asText();assertThat(state).isIn("COMMITTED","RELEASED");assertThat(balances()).startsWith(state.equals("COMMITTED")?8:10,0,state.equals("COMMITTED")?2:0,10);}
 @Test void commitWaitsForStockLockThenUsesFreshDatabaseTime()throws Exception{
  var id=UUID.randomUUID();assertThat(reserve(second,id,UUID.randomUUID(),2).statusCode()).isEqualTo(201);var expires=deadline(id);
  try(var lock=owner();var pool=Executors.newSingleThreadExecutor()){
   lock.setAutoCommit(false);try(var s=lock.createStatement()){s.executeQuery("SELECT sku FROM catalog_item FOR UPDATE").close();}
   var result=pool.submit(()->command(first,id,"commit"));try{waiting(1,"catalog_item");past(expires);lock.commit();error(result.get(20,TimeUnit.SECONDS),409,"RESERVATION_EXPIRED");}finally{lock.rollback();}
  }assertThat(balances()).startsWith(10,0,0,10);assertThat(node(request(first,id.toString(),"GET",null)).get("state").asText()).isEqualTo("EXPIRED");
 }
 @Test void releaseAndExpiryCompeteWithoutDoubleRestoration()throws Exception{
  var id=UUID.randomUUID();assertThat(reserve(second,id,UUID.randomUUID(),2).statusCode()).isEqualTo(201);var expires=deadline(id);
  try(var lock=owner();var pool=Executors.newFixedThreadPool(2)){
   lock.setAutoCommit(false);try(var s=lock.createStatement()){s.executeQuery("SELECT reservation_id FROM reservation FOR UPDATE").close();}
   var release=pool.submit(()->command(first,id,"release"));var expiry=pool.submit(()->second.getBean(ReservationTransactions.class).expireIfDue(id));
   try{waiting(2,"FROM reservation WHERE");past(expires);lock.commit();assertThat(release.get(20,TimeUnit.SECONDS).statusCode()).isEqualTo(200);assertThat(expiry.get(20,TimeUnit.SECONDS).reservation().state().name()).isEqualTo("EXPIRED");}finally{lock.rollback();}
  }assertThat(balances()).startsWith(10,0,0,10);assertThat(command(first,id,"release").statusCode()).isEqualTo(200);
 }
 @Test void commitAndExpiryCompeteAfterDeadline()throws Exception{
  var id=UUID.randomUUID();assertThat(reserve(second,id,UUID.randomUUID(),2).statusCode()).isEqualTo(201);var expires=deadline(id);
  try(var lock=owner();var pool=Executors.newFixedThreadPool(2)){
   lock.setAutoCommit(false);try(var s=lock.createStatement()){s.executeQuery("SELECT reservation_id FROM reservation FOR UPDATE").close();}
   var commit=pool.submit(()->command(first,id,"commit"));var expiry=pool.submit(()->second.getBean(ReservationTransactions.class).expireIfDue(id));
   try{waiting(2,"FROM reservation WHERE");past(expires);lock.commit();error(commit.get(20,TimeUnit.SECONDS),409,"RESERVATION_EXPIRED");assertThat(expiry.get(20,TimeUnit.SECONDS).reservation().state().name()).isEqualTo("EXPIRED");}finally{lock.rollback();}
  }assertThat(balances()).startsWith(10,0,0,10);
 }
 @Test void readIsPersistedStateAndReleaseAfterDeadlineExpires()throws Exception{var id=UUID.randomUUID();assertThat(reserve(second,id,UUID.randomUUID(),2).statusCode()).isEqualTo(201);past(deadline(id));assertThat(node(request(first,id.toString(),"GET",null)).get("state").asText()).isEqualTo("ACTIVE");assertThat(balances()).startsWith(8,2,0,10);var released=command(first,id,"release");assertThat(node(released).get("state").asText()).isEqualTo("EXPIRED");error(command(first,id,"commit"),409,"RESERVATION_EXPIRED");assertThat(balances()).startsWith(10,0,0,10);}
 @Test void durableRejectionCannotAllocateAfterStockReturns()throws Exception{var held=UUID.randomUUID();reserve(first,held,UUID.randomUUID(),10);var id=UUID.randomUUID();var p=UUID.randomUUID();error(reserve(second,id,p,1),409,"INSUFFICIENT_STOCK");assertThat(command(first,held,"release").statusCode()).isEqualTo(200);error(reserve(first,id,p,1),409,"INSUFFICIENT_STOCK");assertThat(count("reservation")).isEqualTo(1);assertThat(balances()).startsWith(10,0,0,10);}
 @Test void unknownSkuAndOverflowAreRetained()throws Exception{
  var missing=UUID.randomUUID();var purchase=UUID.randomUUID();String unknown=payload(purchase,1).replace("AEGIS_ITEM","UNKNOWN");error(request(first,missing.toString(),"PUT",unknown),404,"ITEM_NOT_FOUND");sql("INSERT INTO catalog_item(sku,unit_price_minor,currency,available_quantity) VALUES ('UNKNOWN',1,'INR',1)");error(request(second,missing.toString(),"PUT",unknown),404,"ITEM_NOT_FOUND");
  sql("UPDATE catalog_item SET unit_price_minor=9223372036854775807 WHERE sku='AEGIS_ITEM'");var id=UUID.randomUUID();var p=UUID.randomUUID();error(reserve(first,id,p,2),400,"MONETARY_OVERFLOW");sql("UPDATE catalog_item SET unit_price_minor=1 WHERE sku='AEGIS_ITEM'");error(reserve(second,id,p,2),400,"MONETARY_OVERFLOW");assertThat(count("reservation")).isZero();balances();
 }
 @Test void rollbackAfterAllocationErasesIdentityAndStockMovement()throws Exception{
  sql("CREATE FUNCTION test_fail_insert() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test-only secret SQL failure'; END $$; CREATE TRIGGER test_fail_insert AFTER INSERT ON reservation FOR EACH ROW EXECUTE FUNCTION test_fail_insert()");
  var id=UUID.randomUUID();var p=UUID.randomUUID();try{error(reserve(first,id,p,2),500,"INTERNAL_ERROR");assertThat(count("reservation_identity")).isZero();assertThat(count("reservation_request")).isZero();assertThat(count("reservation")).isZero();assertThat(balances()).startsWith(10,0,0,10);}finally{sql("DROP TRIGGER test_fail_insert ON reservation; DROP FUNCTION test_fail_insert()");}assertThat(reserve(second,id,p,2).statusCode()).isEqualTo(201);
 }
 @Test void rollbackAfterCounterTransitionPreservesActiveReservation()throws Exception{
  var id=UUID.randomUUID();reserve(first,id,UUID.randomUUID(),2);sql("CREATE FUNCTION test_fail_update() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test-only secret SQL failure'; END $$; CREATE TRIGGER test_fail_update AFTER UPDATE ON reservation FOR EACH ROW EXECUTE FUNCTION test_fail_update()");
  try{error(command(first,id,"commit"),500,"INTERNAL_ERROR");assertThat(node(request(second,id.toString(),"GET",null)).get("state").asText()).isEqualTo("ACTIVE");assertThat(balances()).startsWith(8,2,0,10);}finally{sql("DROP TRIGGER test_fail_update ON reservation; DROP FUNCTION test_fail_update()");}assertThat(command(second,id,"commit").statusCode()).isEqualTo(200);
 }
 @Test void releasedCannotCommitAndRepeatReleaseIsStable()throws Exception{var id=UUID.randomUUID();reserve(first,id,UUID.randomUUID(),2);var r=command(first,id,"release");assertThat(node(r).get("state").asText()).isEqualTo("RELEASED");assertThat(node(command(second,id,"release"))).isEqualTo(node(r));error(command(second,id,"commit"),409,"RESERVATION_RELEASED");assertThat(balances()).startsWith(10,0,0,10);}
 @Test void invalidInputsAndUnknownIdentitiesHaveContractCodes()throws Exception{error(request(first,"1-1-1-1-1","GET",null),400,"INVALID_REQUEST");var id=UUID.randomUUID();error(request(first,id.toString(),"GET",null),404,"RESERVATION_NOT_FOUND");error(command(first,id,"commit"),404,"RESERVATION_NOT_FOUND");error(command(first,id,"release"),404,"RESERVATION_NOT_FOUND");error(reserve(first,id,UUID.randomUUID(),0),400,"INVALID_REQUEST");error(request(first,id.toString(),"PUT",payload(UUID.randomUUID(),1).replace("AEGIS_ITEM","bad")),400,"INVALID_REQUEST");assertThat(count("reservation_identity")).isZero();}
 @Test void runtimeRoleCannotRewritePayloadOrCrossDatabase()throws Exception{
  try(var c=DriverManager.getConnection(DB.getJdbcUrl(),"inventory_writer","test-only-password");var s=c.createStatement()){
   for(String forbidden:List.of("CREATE TABLE forbidden(id int)","DELETE FROM reservation","TRUNCATE reservation","UPDATE catalog_item SET initial_quantity=1","UPDATE reservation SET quantity=1","UPDATE reservation_request SET quantity=1"))assertThatThrownBy(()->s.execute(forbidden)).isInstanceOf(SQLException.class);
  }assertThatThrownBy(()->DriverManager.getConnection(DB.getJdbcUrl().replace("/aegis_inventory","/other_service"),"inventory_writer","test-only-password")).isInstanceOf(SQLException.class);
 }
 @ParameterizedTest @ValueSource(strings={"available_quantity=-1","reserved_quantity=-1","sold_quantity=-1","available_quantity=9","initial_quantity=11","available_quantity=9,reserved_quantity=1"})
 void databaseRejectsNegativeConservationAndLedgerDrift(String assignment){assertThatThrownBy(()->sql("UPDATE catalog_item SET "+assignment)).isInstanceOf(SQLException.class);}
 @Test void restartPreservesReservationAndReplay()throws Exception{var id=UUID.randomUUID();var p=UUID.randomUUID();var created=reserve(first,id,p,2);second.close();second=startInstance("2s");assertThat(node(request(second,id.toString(),"GET",null))).isEqualTo(node(created));assertThat(reserve(second,id,p,2).statusCode()).isEqualTo(200);assertThat(balances()).startsWith(8,2,0,10);}
 @Test void lostCommitAcknowledgementReplaysAfterDeadlineWithoutSecondSale()throws Exception{
  var id=UUID.randomUUID();assertThat(reserve(second,id,UUID.randomUUID(),2).statusCode()).isEqualTo(201);var expires=deadline(id);
  var completed=new CompletableFuture<HttpResponse<String>>();
  // Test-only forwarding server deliberately drops the downstream HTTP acknowledgement AFTER durable commit.
  var proxy=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("localhost",0),0);
  proxy.createContext("/commit",exchange->{try{completed.complete(command(first,id,"commit"));}catch(Exception e){completed.completeExceptionally(e);}finally{exchange.close();}});proxy.start();
  try{
   assertThatThrownBy(()->http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+proxy.getAddress().getPort()+"/commit")).timeout(Duration.ofSeconds(10)).PUT(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString())).isInstanceOf(java.io.IOException.class);
   assertThat(completed.get(15,TimeUnit.SECONDS).statusCode()).isEqualTo(200);assertThat(balances()).startsWith(8,0,2,10);
  }finally{proxy.stop(0);}
  past(expires);var replay=command(second,id,"commit");assertThat(replay.statusCode()).isEqualTo(200);assertThat(node(replay).get("state").asText()).isEqualTo("COMMITTED");assertThat(balances()).startsWith(8,0,2,10);
 }
 @ParameterizedTest @ValueSource(booleans={false,true})
 void identityContenderWaitsForCommitOrRollback(boolean commit)throws Exception{
  var id=UUID.randomUUID();var p=UUID.randomUUID();
  try(var lock=owner();var pool=Executors.newSingleThreadExecutor()){
   lock.setAutoCommit(false);try(var s=lock.prepareStatement("INSERT INTO reservation_identity VALUES (?,?,'AEGIS_ITEM',20)")){s.setObject(1,id);s.setObject(2,p);s.executeUpdate();}
   try(var s=lock.prepareStatement("INSERT INTO reservation_request(reservation_id,purchase_id,sku,quantity,outcome,error_code) VALUES (?,?,'AEGIS_ITEM',20,'REJECTED','INSUFFICIENT_STOCK')")){s.setObject(1,id);s.setObject(2,p);s.executeUpdate();}
   var result=pool.submit(()->reserve(second,id,p,20));try{waiting(1,"INSERT INTO reservation_identity");assertThat(result.isDone()).isFalse();if(commit)lock.commit();else lock.rollback();error(result.get(20,TimeUnit.SECONDS),409,"INSUFFICIENT_STOCK");assertThat(count("reservation_identity")).isEqualTo(1);assertThat(count("reservation_request")).isEqualTo(1);}finally{lock.rollback();}
  }assertThat(balances()).startsWith(10,0,0,10);
 }
 @ParameterizedTest @ValueSource(strings={"","/commit","/release"})
 void invalidMutationUuidNeverCreatesIdentity(String suffix)throws Exception{error(request(first,"1-1-1-1-1"+suffix,"PUT",suffix.isEmpty()?payload(UUID.randomUUID(),1):null),400,"INVALID_REQUEST");assertThat(count("reservation_identity")).isZero();}
 @Test void positiveSignedLongMaximumPriceIsRepresentable()throws Exception{sql("UPDATE catalog_item SET unit_price_minor=9223372036854775807");var r=reserve(first,UUID.randomUUID(),UUID.randomUUID(),1);assertThat(r.statusCode()).as(r.body()).isEqualTo(201);assertThat(node(r).get("totalAmountMinor").asLong()).isEqualTo(Long.MAX_VALUE);assertThat(balances()).startsWith(9,1,0,10);}
}
