package io.aegis.checkout;

import io.aegis.checkout.domain.Workflow;
import io.aegis.checkout.infrastructure.persistence.WorkflowRepository;
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
 @Autowired WorkflowRepository workflows; @Autowired JdbcClient jdbc; @Autowired PlatformTransactionManager manager; @Autowired Flyway flyway;
 @BeforeEach void clean()throws SQLException{FoundationDatabase.sql("TRUNCATE purchase CASCADE");}
 Workflow initialize(UUID key){return workflows.initialize(key,"AEGIS_ITEM",2,Workflow.Ids.allocate());}
 long count(String table){return jdbc.sql("SELECT count(*) FROM "+table).query(Long.class).single();}
 @Test void stableIdentityAndSemanticReplay(){
  var key=UUID.randomUUID();var first=initialize(key);var replay=initialize(key);
  assertThat(replay).isEqualTo(first);assertThat(first.leaseUntil()).isNull();assertThat(first.generation()).isZero();
  assertThat(count("purchase")).isEqualTo(1);assertThat(count("workflow_intent")).isEqualTo(1);
  assertThat(jdbc.sql("SELECT state FROM purchase").query(String.class).single()).isEqualTo("PROCESSING");
 }
 @Test void changedFingerprintCannotOverwrite(){var key=UUID.randomUUID();var row=initialize(key);
  assertThatThrownBy(()->workflows.initialize(key,"AEGIS_ITEM",3,Workflow.Ids.allocate())).isInstanceOf(WorkflowRepository.FingerprintConflict.class);
  assertThat(initialize(key)).isEqualTo(row);assertThat(count("purchase_key")).isEqualTo(1);
 }
 @Test void concurrentDuplicatesShareOneCommittedIdentity()throws Exception{
  var key=UUID.randomUUID();var ready=new CountDownLatch(6);var go=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(6)){
   var futures=new ArrayList<Future<Workflow>>();for(int i=0;i<6;i++)futures.add(pool.submit(()->{ready.countDown();if(!go.await(10,TimeUnit.SECONDS))throw new AssertionError();return initialize(key);}));
   assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();go.countDown();var winner=futures.getFirst().get(20,TimeUnit.SECONDS);
   for(var f:futures)assertThat(f.get(20,TimeUnit.SECONDS)).isEqualTo(winner);assertThat(count("purchase")).isEqualTo(1);
  }finally{go.countDown();}
 }
 @Test void concurrentConflictingFingerprintsHaveOneWinner()throws Exception {
  var key=UUID.randomUUID();var ready=new CountDownLatch(6);var go=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(6)) {
   var futures=new ArrayList<Future<Boolean>>();for(int i=1;i<=6;i++){final int quantity=i;futures.add(pool.submit(()->{ready.countDown();if(!go.await(10,TimeUnit.SECONDS))throw new AssertionError();try{workflows.initialize(key,"AEGIS_ITEM",quantity,Workflow.Ids.allocate());return true;}catch(WorkflowRepository.FingerprintConflict expected){return false;}}));}
   assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();go.countDown();int winners=0;for(var f:futures)if(f.get(20,TimeUnit.SECONDS))winners++;
   assertThat(winners).isEqualTo(1);assertThat(count("purchase_key")).isEqualTo(1);assertThat(count("purchase")).isEqualTo(1);
  }finally{go.countDown();}
 }
 @Test void rollbackRemovesBindingPurchaseAndIntent(){
  assertThatThrownBy(()->new TransactionTemplate(manager).execute(status->{initialize(UUID.randomUUID());throw new IllegalStateException("test rollback");})).isInstanceOf(IllegalStateException.class);
  for(var table:List.of("purchase","purchase_key","purchase_workflow","workflow_intent"))assertThat(count(table)).isZero();
 }
 @Test void invalidAdmissionRollsBackKey(){assertThatThrownBy(()->workflows.initialize(UUID.randomUUID(),"bad",2,Workflow.Ids.allocate())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);assertThat(count("purchase_key")).isZero();}
 @Test void fencingAndTerminalEvidenceDoNotRegress()throws Exception{
  var row=initialize(UUID.randomUUID());var a=UUID.randomUUID();var b=UUID.randomUUID();var first=workflows.claim(row.purchaseId(),a,15).orElseThrow();
  assertThat(workflows.claim(row.purchaseId(),b,15)).isEmpty();
  FoundationDatabase.sql("UPDATE purchase_workflow SET lease_until=clock_timestamp()-interval '1 second',revision=revision+1");
  var second=workflows.claim(row.purchaseId(),b,15).orElseThrow();assertThat(second.generation()).isEqualTo(first.generation()+1);
  assertThat(workflows.observe(row.purchaseId(),a,first.generation(),first.revision(),"RESERVE","ACTIVE")).isFalse();
  assertThat(workflows.observe(row.purchaseId(),b,second.generation(),second.revision(),"RESERVE","COMMITTED")).isTrue();
  var latest=workflows.find(row.purchaseId()).orElseThrow();
  assertThatThrownBy(()->workflows.observe(row.purchaseId(),b,latest.generation(),latest.revision(),"RESERVE","ACTIVE")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThat(jdbc.sql("SELECT outcome FROM workflow_observation").query(String.class).single()).isEqualTo("COMMITTED");
  assertThat(workflows.find(row.purchaseId()).orElseThrow().revision()).isEqualTo(latest.revision());
 }
 @Test void pausedWorkflowCannotBeClaimed()throws Exception{initialize(UUID.randomUUID());FoundationDatabase.sql("UPDATE purchase_workflow SET dispatch_paused=true,revision=revision+1");var id=jdbc.sql("SELECT purchase_id FROM purchase").query(UUID.class).single();assertThat(workflows.claim(id,UUID.randomUUID(),15)).isEmpty();}
 @ParameterizedTest @ValueSource(strings={"generation=-1,revision=revision+1","lease_owner='11111111-1111-4111-8111-111111111111',revision=revision+1","lease_until='infinity',revision=revision+1","cleanup_required=NULL,revision=revision+1","attempt_count=-1,revision=revision+1","revision=revision","payment_id='11111111-1111-4111-8111-111111111111',revision=revision+1"})
 void invalidMetadataRejected(String assignment){initialize(UUID.randomUUID());assertThatThrownBy(()->jdbc.sql("UPDATE purchase_workflow SET "+assignment).update()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);}
 @ParameterizedTest @ValueSource(strings={"UPDATE purchase_key SET quantity=3","UPDATE workflow_intent SET quantity=3","UPDATE purchase SET quantity=3","INSERT INTO notification_intent SELECT purchase_id,notification_id,'PURCHASE_CONFIRMED',NULL FROM purchase_workflow"})
 void immutablePayloadAndNotificationPrecondition(String sql){initialize(UUID.randomUUID());assertThatThrownBy(()->new TransactionTemplate(manager).execute(s->{jdbc.sql(sql).update();return null;})).isInstanceOf(RuntimeException.class);}
 @Test void refundMayRemainRequiredAfterInventoryCleanupSettles()throws Exception {
  initialize(UUID.randomUUID());FoundationDatabase.sql("UPDATE purchase_workflow SET refund_required=true,cleanup_required=false,revision=revision+1");
  assertThat(jdbc.sql("SELECT refund_required AND NOT cleanup_required FROM purchase_workflow").query(Boolean.class).single()).isTrue();
 }
 @Test void terminalPurchaseCannotAcquireOrUseBusinessClaim()throws Exception {
  var row=initialize(UUID.randomUUID());var owner=UUID.randomUUID();var claim=workflows.claim(row.purchaseId(),owner,15).orElseThrow();
  // Hypothetical stored terminal snapshot, not evidence of executed downstream work.
  FoundationDatabase.sql("UPDATE purchase SET state='FAILED',step='SETTLED'");
  assertThat(workflows.observe(row.purchaseId(),owner,claim.generation(),claim.revision(),"RESERVE","EXPIRED")).isFalse();
  FoundationDatabase.sql("UPDATE purchase_workflow SET lease_until=clock_timestamp()-interval '1 second',revision=revision+1");
  assertThat(workflows.claim(row.purchaseId(),UUID.randomUUID(),15)).isEmpty();
 }
 @Test void migrationsAndRestrictedRole()throws Exception{
  flyway.validate();assertThat(flyway.info().applied()).hasSize(2);
  assertThatThrownBy(()->jdbc.sql("CREATE TABLE forbidden(id int)").update()).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->jdbc.sql("DELETE FROM purchase_workflow").update()).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->jdbc.sql("SELECT * FROM flyway_schema_history").query().listOfRows()).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->DriverManager.getConnection(DB.getJdbcUrl().replace("/aegis_checkout","/other_service"),"foundation_writer","test-only-password")).isInstanceOf(SQLException.class);
 }
 @Test void v1DataSurvivesV2Upgrade()throws Exception {
  String schema="upgrade_fixture";
  Flyway.configure().dataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()).schemas(schema).defaultSchema(schema).target("1").load().migrate();
  String before;
  try(var c=FoundationDatabase.owner();var q=c.createStatement()){
   q.execute("SET search_path TO "+schema);q.execute("INSERT INTO purchase VALUES ('11111111-1111-4111-8111-111111111111','AEGIS_ITEM',2,NULL,NULL,'PROCESSING','RESERVATION_PENDING',false,false,'2026-10-24T00:00:00Z','2026-10-24T00:00:00Z')");
   try(var rows=q.executeQuery("SELECT row_to_json(purchase)::text FROM purchase")){rows.next();before=rows.getString(1);}
  }
  var upgraded=Flyway.configure().dataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()).schemas(schema).defaultSchema(schema).load();upgraded.migrate();upgraded.validate();
  assertThat(Arrays.stream(upgraded.info().applied()).filter(m->"SQL".equals(m.getType().toString())).count()).isEqualTo(2);
  try(var c=FoundationDatabase.owner();var q=c.createStatement()){
   q.execute("SET search_path TO "+schema);try(var rows=q.executeQuery("SELECT row_to_json(purchase)::text FROM purchase")){rows.next();assertThat(rows.getString(1)).isEqualTo(before);}
  }
 }

}
