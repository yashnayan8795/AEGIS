package io.aegis.payment;

import io.aegis.payment.domain.*;
import io.aegis.payment.infrastructure.persistence.PaymentFoundationRepository;
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
 @Autowired PaymentFoundationRepository store;@Autowired JdbcClient jdbc;@Autowired PlatformTransactionManager manager;@Autowired Flyway flyway;
 @BeforeEach void clean()throws SQLException{FoundationDatabase.sql("TRUNCATE payment CASCADE");}
 Payment pending(){return store.admitCharge(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),200,"INR",SyntheticPolicy.normal());}
 Payment successfulFixture(){var row=pending();jdbc.sql("UPDATE payment SET state='SUCCEEDED',updated_at=GREATEST(updated_at,clock_timestamp()) WHERE payment_id=:id").param("id",row.paymentId()).update();return row;}
 long count(String table){return jdbc.sql("SELECT count(*) FROM "+table).query(Long.class).single();}
 @Test void admissionStoresPolicyWithoutExecutingCharge(){var row=pending();assertThat(row.state()).isEqualTo(Payment.State.PENDING);assertThat(row.declineCode()).isNull();assertThat(store.policy(row.paymentId())).contains(SyntheticPolicy.normal());}
 @Test void replayCannotChooseNewPolicy(){var row=pending();var replay=store.admitCharge(row.paymentId(),row.purchaseId(),row.reservationId(),200,"INR",new SyntheticPolicy("FUTURE_VERSION","OTHER","DECLINED"));assertThat(replay).isEqualTo(row);assertThat(store.policy(row.paymentId())).contains(SyntheticPolicy.normal());assertThat(count("payment")).isEqualTo(1);}
 @Test void conflictingPayloadRollsBack(){var row=pending();assertThatThrownBy(()->store.admitCharge(row.paymentId(),row.purchaseId(),UUID.randomUUID(),200,"INR",SyntheticPolicy.normal())).isInstanceOf(PaymentFoundationRepository.PayloadConflict.class);assertThat(count("payment")).isEqualTo(1);}
 @Test void oneChargePerPurchase(){var row=pending();assertThatThrownBy(()->store.admitCharge(UUID.randomUUID(),row.purchaseId(),UUID.randomUUID(),200,"INR",SyntheticPolicy.normal())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);}
 @Test void concurrentChargeAdmissionPreservesOnePolicy()throws Exception {
  var id=UUID.randomUUID();var purchase=UUID.randomUUID();var reservation=UUID.randomUUID();var ready=new CountDownLatch(6);var go=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(6)) {
   var futures=new ArrayList<Future<Payment>>();for(int i=0;i<6;i++)futures.add(pool.submit(()->{ready.countDown();if(!go.await(10,TimeUnit.SECONDS))throw new AssertionError();return store.admitCharge(id,purchase,reservation,200,"INR",SyntheticPolicy.normal());}));
   assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();go.countDown();var winner=futures.getFirst().get(20,TimeUnit.SECONDS);for(var f:futures)assertThat(f.get(20,TimeUnit.SECONDS)).isEqualTo(winner);
   assertThat(count("payment")).isEqualTo(1);assertThat(count("charge_policy")).isEqualTo(1);
  }finally{go.countDown();}
 }
 @Test void concurrentRefundIdentitiesCannotBypassParentUniqueness()throws Exception {
  var payment=successfulFixture();var ready=new CountDownLatch(4);var go=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(4)) {
   var futures=new ArrayList<Future<Boolean>>();for(int i=0;i<4;i++)futures.add(pool.submit(()->{ready.countDown();if(!go.await(10,TimeUnit.SECONDS))throw new AssertionError();try{store.admitRefund(UUID.randomUUID(),payment.paymentId(),200,"INR","RESERVATION_EXPIRED",SyntheticPolicy.normal());return true;}catch(org.springframework.dao.DataIntegrityViolationException expected){return false;}}));
   assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();go.countDown();int winners=0;for(var f:futures)if(f.get(20,TimeUnit.SECONDS))winners++;assertThat(winners).isEqualTo(1);assertThat(count("refund")).isEqualTo(1);
  }finally{go.countDown();}
 }
 @Test void legacyPendingCannotAcquireInventedPolicy()throws SQLException{
  var id=UUID.randomUUID();var purchase=UUID.randomUUID();var reservation=UUID.randomUUID();
  FoundationDatabase.sql("INSERT INTO payment VALUES ('"+id+"','"+purchase+"','"+reservation+"',200,'INR','PENDING',NULL,'2026-10-24T00:00:00Z','2026-10-24T00:00:00Z')");
  assertThatThrownBy(()->store.admitCharge(id,purchase,reservation,200,"INR",SyntheticPolicy.normal())).isInstanceOf(IllegalStateException.class);assertThat(store.policy(id)).isEmpty();
 }
 @Test void invalidPolicyRollsBackCharge(){assertThatThrownBy(()->store.admitCharge(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),200,"INR",new SyntheticPolicy("VERSION","SCENARIO","FAILED"))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);assertThat(count("payment")).isZero();}
 @Test void enclosingRollbackRemovesChargeAndPolicy(){assertThatThrownBy(()->new TransactionTemplate(manager).execute(s->{pending();throw new IllegalStateException();})).isInstanceOf(IllegalStateException.class);assertThat(count("payment")).isZero();assertThat(count("charge_policy")).isZero();}
 @Test void refundStorageMappingAndReplay(){var payment=successfulFixture();var id=UUID.randomUUID();var row=store.admitRefund(id,payment.paymentId(),200,"INR","RESERVATION_EXPIRED",SyntheticPolicy.normal());assertThat(row.state()).isEqualTo(RefundRecord.State.PENDING);assertThat(row.policy()).isEqualTo(SyntheticPolicy.normal());assertThat(row.createdAt()).isEqualTo(row.updatedAt());assertThat(store.refund(id)).contains(row);assertThat(store.admitRefund(id,payment.paymentId(),200,"INR","RESERVATION_EXPIRED",new SyntheticPolicy("FUTURE_VERSION","OTHER","FAILED"))).isEqualTo(row);}
 @Test void oneRefundPerChargeAndMatchingFullAmount(){var payment=successfulFixture();store.admitRefund(UUID.randomUUID(),payment.paymentId(),200,"INR","RESERVATION_EXPIRED",SyntheticPolicy.normal());assertThatThrownBy(()->store.admitRefund(UUID.randomUUID(),payment.paymentId(),200,"INR","RESERVATION_EXPIRED",SyntheticPolicy.normal())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);assertThat(count("refund")).isEqualTo(1);}
 @Test void refundRequiresSuccessfulCharge(){var payment=pending();assertThatThrownBy(()->store.admitRefund(UUID.randomUUID(),payment.paymentId(),200,"INR","RESERVATION_EXPIRED",SyntheticPolicy.normal())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);assertThat(count("refund")).isZero();}
 @Test void wrongFullAmountRejected(){var payment=successfulFixture();assertThatThrownBy(()->store.admitRefund(UUID.randomUUID(),payment.paymentId(),100,"INR","RESERVATION_EXPIRED",SyntheticPolicy.normal())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);}
 @Test void failedRefundCannotBeRetriedAsNewOutcome(){var payment=successfulFixture();var id=UUID.randomUUID();store.admitRefund(id,payment.paymentId(),200,"INR","RESERVATION_EXPIRED",new SyntheticPolicy("TEST_ONLY","FAIL","FAILED"));jdbc.sql("UPDATE refund SET state='FAILED'").update();assertThatThrownBy(()->jdbc.sql("UPDATE refund SET state='PENDING'").update()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);assertThat(store.admitRefund(id,payment.paymentId(),200,"INR","RESERVATION_EXPIRED",SyntheticPolicy.normal()).state()).isEqualTo(RefundRecord.State.FAILED);}
 @ParameterizedTest @ValueSource(strings={"created_at='-infinity'","updated_at='infinity'","created_at='0001-01-01 BC'","updated_at='10000-01-01'","amount_minor=201","state='DECLINED',decline_code='SIMULATED_DECLINE'"})
 void invalidTimeOrImmutableChargeRejected(String assignment){successfulFixture();assertThatThrownBy(()->jdbc.sql("UPDATE payment SET "+assignment).update()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);}
 @Test void policyCannotBeRewritten(){pending();assertThatThrownBy(()->jdbc.sql("UPDATE charge_policy SET policy_version='OTHER'").update()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);}
 @Test void migrationsAndRestrictedRole()throws Exception{flyway.validate();assertThat(flyway.info().applied()).hasSize(2);assertThatThrownBy(()->jdbc.sql("CREATE TABLE forbidden(id int)").update()).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThatThrownBy(()->jdbc.sql("DELETE FROM refund").update()).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThatThrownBy(()->DriverManager.getConnection(DB.getJdbcUrl().replace("/aegis_payment","/other_service"),"foundation_writer","test-only-password")).isInstanceOf(SQLException.class);}
 @Test void v1DataSurvivesV2Upgrade()throws Exception {
  String schema="upgrade_fixture";
  Flyway.configure().dataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()).schemas(schema).defaultSchema(schema).target("1").load().migrate();
  String before;
  try(var c=FoundationDatabase.owner();var q=c.createStatement()){
   q.execute("SET search_path TO "+schema);q.execute("INSERT INTO payment VALUES ('11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222','33333333-3333-4333-8333-333333333333',200,'INR','PENDING',NULL,'2026-10-24T00:00:00Z','2026-10-24T00:00:00Z')");
   try(var rows=q.executeQuery("SELECT row_to_json(payment)::text FROM payment")){rows.next();before=rows.getString(1);}
  }
  var upgraded=Flyway.configure().dataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()).schemas(schema).defaultSchema(schema).load();upgraded.migrate();upgraded.validate();
  assertThat(Arrays.stream(upgraded.info().applied()).filter(m->"SQL".equals(m.getType().toString())).count()).isEqualTo(2);
  try(var c=FoundationDatabase.owner();var q=c.createStatement()){
   q.execute("SET search_path TO "+schema);try(var rows=q.executeQuery("SELECT row_to_json(payment)::text FROM payment")){rows.next();assertThat(rows.getString(1)).isEqualTo(before);}
  }
 }

 @Test void migrationRefusesInvalidLegacyTimestampsInsteadOfRepairingThem()throws Exception {
  String schema="invalid_legacy_fixture";
  Flyway.configure().dataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()).schemas(schema).defaultSchema(schema).target("1").load().migrate();
  try(var c=FoundationDatabase.owner();var q=c.createStatement()){
   q.execute("SET search_path TO "+schema);
   q.execute("INSERT INTO payment VALUES ('11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222','33333333-3333-4333-8333-333333333333',200,'INR','PENDING',NULL,'infinity','infinity')");
  }
  var upgrade=Flyway.configure().dataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()).schemas(schema).defaultSchema(schema).load();
  assertThatThrownBy(upgrade::migrate).isInstanceOf(org.flywaydb.core.api.FlywayException.class);
  assertThat(Arrays.stream(upgrade.info().applied()).filter(m->"SQL".equals(m.getType().toString())).count()).isEqualTo(1);
  try(var c=FoundationDatabase.owner();var q=c.createStatement()){
   q.execute("SET search_path TO "+schema);try(var r=q.executeQuery("SELECT isfinite(created_at) FROM payment")){r.next();assertThat(r.getBoolean(1)).isFalse();}
  }
 }

}
