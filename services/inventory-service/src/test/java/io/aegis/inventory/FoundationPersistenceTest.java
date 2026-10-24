package io.aegis.inventory;

import io.aegis.inventory.infrastructure.persistence.ReservationRepository;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
class FoundationPersistenceTest {
 @Container static final org.testcontainers.postgresql.PostgreSQLContainer DB=FoundationDatabase.DB;
 @DynamicPropertySource static void database(DynamicPropertyRegistry p)throws SQLException{FoundationDatabase.configure(p);}
 @Autowired ReservationRepository requests;@Autowired JdbcClient jdbc;@Autowired PlatformTransactionManager manager;@Autowired Flyway flyway;
 @BeforeEach void clean()throws SQLException{FoundationDatabase.sql("TRUNCATE catalog_item,reservation_request,reservation CASCADE; INSERT INTO catalog_item(sku,unit_price_minor,currency,available_quantity) VALUES ('AEGIS_ITEM',100,'INR',10)");}
 long count(String table){return jdbc.sql("SELECT count(*) FROM "+table).query(Long.class).single();}
 @Test void rejectionRetainsDecisionAfterStockChanges(){var id=UUID.randomUUID();var purchase=UUID.randomUUID();
  var first=requests.retainRejection(id,purchase,"AEGIS_ITEM",20,"INSUFFICIENT_STOCK");try {FoundationDatabase.sql("TRUNCATE catalog_item CASCADE; INSERT INTO catalog_item(sku,unit_price_minor,currency,available_quantity) VALUES ('AEGIS_ITEM',100,'INR',100)");}catch(SQLException e){throw new AssertionError(e);}
  assertThat(requests.retainRejection(id,purchase,"AEGIS_ITEM",20,"ITEM_NOT_FOUND")).isEqualTo(first);
  assertThat(count("reservation_request")).isEqualTo(1);assertThat(requests.find(id)).isEmpty();
 }
 @Test void conflictingFingerprintAndPurchaseIdentityRejected(){var id=UUID.randomUUID();var purchase=UUID.randomUUID();requests.retainRejection(id,purchase,"AEGIS_ITEM",2,"INSUFFICIENT_STOCK");
  assertThatThrownBy(()->requests.retainRejection(id,purchase,"AEGIS_ITEM",3,"INSUFFICIENT_STOCK")).isInstanceOf(ReservationRepository.RequestConflict.class);
  assertThatThrownBy(()->requests.retainRejection(UUID.randomUUID(),purchase,"AEGIS_ITEM",2,"INSUFFICIENT_STOCK")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThat(count("reservation_request")).isEqualTo(1);
 }
 @Test void concurrentRejectedRequestsHaveOneWinner()throws Exception{
  var id=UUID.randomUUID();var purchase=UUID.randomUUID();var ready=new CountDownLatch(6);var go=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(6)){
   var futures=new ArrayList<Future<ReservationRepository.RequestOutcome>>();for(int i=0;i<6;i++)futures.add(pool.submit(()->{ready.countDown();if(!go.await(10,TimeUnit.SECONDS))throw new AssertionError();return requests.retainRejection(id,purchase,"AEGIS_ITEM",2,"INSUFFICIENT_STOCK");}));
   assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();go.countDown();var winner=futures.getFirst().get(20,TimeUnit.SECONDS);
   for(var f:futures)assertThat(f.get(20,TimeUnit.SECONDS)).isEqualTo(winner);assertThat(count("reservation_request")).isEqualTo(1);
  }finally{go.countDown();}
 }
 @Test void rollbackDoesNotRetainFalseRejection(){assertThatThrownBy(()->new TransactionTemplate(manager).execute(s->{requests.retainRejection(UUID.randomUUID(),UUID.randomUUID(),"AEGIS_ITEM",2,"INSUFFICIENT_STOCK");throw new IllegalStateException();})).isInstanceOf(IllegalStateException.class);assertThat(count("reservation_request")).isZero();}
 UUID fixture(){var id=UUID.randomUUID();var purchase=UUID.randomUUID();new TransactionTemplate(manager).execute(s->{
  jdbc.sql("INSERT INTO reservation_identity VALUES (:id,:purchase,'AEGIS_ITEM',2)").param("id",id).param("purchase",purchase).update();
  jdbc.sql("UPDATE catalog_item SET available_quantity=8,reserved_quantity=2").update();
  jdbc.sql("INSERT INTO reservation_request(reservation_id,purchase_id,sku,quantity,outcome) VALUES (:id,:purchase,'AEGIS_ITEM',2,'CREATED')").param("id",id).param("purchase",purchase).update();
  jdbc.sql("INSERT INTO reservation VALUES (:id,:purchase,'AEGIS_ITEM',2,100,200,'INR','ACTIVE','2026-10-24T00:00:00Z','2026-10-24T00:00:30Z')").param("id",id).param("purchase",purchase).update();return null;
 });return id;}
 @Test void mapsReservationFromConsistentAccountingFixture(){var id=fixture();var row=requests.find(id).orElseThrow();assertThat(row.quantity()).isEqualTo(2);assertThat(row.totalAmountMinor()).isEqualTo(200);assertThat(row.currency()).isEqualTo("INR");assertThat(row.expiresAt()).isAfter(row.createdAt());assertThat(jdbc.sql("SELECT available_quantity FROM catalog_item").query(Long.class).single()).isEqualTo(8);}
 @Test void createdOutcomeRequiresAtomicReservation(){var id=UUID.randomUUID();var purchase=UUID.randomUUID();assertThatThrownBy(()->new TransactionTemplate(manager).execute(s->{jdbc.sql("INSERT INTO reservation_identity VALUES (:id,:purchase,'AEGIS_ITEM',2)").param("id",id).param("purchase",purchase).update();jdbc.sql("INSERT INTO reservation_request(reservation_id,purchase_id,sku,quantity,outcome) VALUES (:id,:purchase,'AEGIS_ITEM',2,'CREATED')").param("id",id).param("purchase",purchase).update();return null;})).isInstanceOf(RuntimeException.class);assertThat(count("reservation_request")).isZero();}
 @ParameterizedTest @ValueSource(strings={"quantity=0","total_amount_minor=201","currency='USD'","expires_at=created_at","expires_at='infinity'","state='OTHER'","unit_price_minor=-1","purchase_id='11111111-1111-4111-8111-111111111111'"})
 void invalidOrImmutableReservationRejected(String assignment){fixture();assertThatThrownBy(()->jdbc.sql("UPDATE reservation SET "+assignment).update()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);}
 @Test void terminalStateCannotReverse(){fixture();new TransactionTemplate(manager).execute(status->{jdbc.sql("UPDATE reservation SET state='EXPIRED'").update();jdbc.sql("UPDATE catalog_item SET available_quantity=10,reserved_quantity=0").update();return null;});assertThatThrownBy(()->jdbc.sql("UPDATE reservation SET state='COMMITTED'").update()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);}
 @Test void rejectionCannotBeOverwritten(){requests.retainRejection(UUID.randomUUID(),UUID.randomUUID(),"AEGIS_ITEM",2,"INSUFFICIENT_STOCK");assertThatThrownBy(()->jdbc.sql("UPDATE reservation_request SET outcome='CREATED',error_code=NULL").update()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);}
 @Test void migrationsAndRestrictedRole()throws Exception{flyway.validate();assertThat(flyway.info().applied()).hasSize(3);
  assertThatThrownBy(()->jdbc.sql("CREATE TABLE forbidden(id int)").update()).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->jdbc.sql("DELETE FROM reservation_request").update()).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->DriverManager.getConnection(DB.getJdbcUrl().replace("/aegis_inventory","/other_service"),"foundation_writer","test-only-password")).isInstanceOf(SQLException.class);
 }
 @Test void v1DataSurvivesV2Upgrade()throws Exception {
  String schema="upgrade_fixture";
  Flyway.configure().dataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()).schemas(schema).defaultSchema(schema).target("1").load().migrate();
  String before;
  try(var c=FoundationDatabase.owner();var q=c.createStatement()){
   q.execute("SET search_path TO "+schema);q.execute("INSERT INTO catalog_item(sku,unit_price_minor,currency,available_quantity) VALUES ('AEGIS_ITEM',100,'INR',10)");
   try(var rows=q.executeQuery("SELECT row_to_json(catalog_item)::text FROM catalog_item")){rows.next();before=rows.getString(1);}
  }
  var upgraded=Flyway.configure().dataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()).schemas(schema).defaultSchema(schema).target("2").load();upgraded.migrate();upgraded.validate();
  assertThat(Arrays.stream(upgraded.info().applied()).filter(m->"SQL".equals(m.getType().toString())).count()).isEqualTo(2);
  try(var c=FoundationDatabase.owner();var q=c.createStatement()){
   q.execute("SET search_path TO "+schema);try(var rows=q.executeQuery("SELECT row_to_json(catalog_item)::text FROM catalog_item")){rows.next();assertThat(rows.getString(1)).isEqualTo(before);}
  }
 }

}
