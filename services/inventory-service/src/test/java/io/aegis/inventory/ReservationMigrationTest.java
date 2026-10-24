package io.aegis.inventory;
import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
@Testcontainers
class ReservationMigrationTest {
 @Container static final PostgreSQLContainer DB=new PostgreSQLContainer("postgres:17.11");
 Flyway version(String schema,String target){var c=Flyway.configure().dataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()).schemas(schema).defaultSchema(schema);if(target!=null)c.target(target);return c.load();}
 void sql(String schema,String sql)throws SQLException{try(var c=DriverManager.getConnection(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword());var s=c.createStatement()){s.execute("SET search_path TO "+schema);s.execute(sql);}}
 @Test void v2CatalogAndRejectionsSurviveV3()throws Exception{
  var schema="rejected_upgrade";version(schema,"2").migrate();sql(schema,"INSERT INTO catalog_item VALUES ('AEGIS_ITEM',125,'INR',10); INSERT INTO reservation_request VALUES ('11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222','AEGIS_ITEM',20,'REJECTED','INSUFFICIENT_STOCK',clock_timestamp())");
  var f=version(schema,null);f.migrate();f.validate();assertThat(java.util.Arrays.stream(f.info().applied()).filter(m->m.getVersion()!=null).count()).isEqualTo(3);
  try(var c=DriverManager.getConnection(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword());var s=c.createStatement()){
   s.execute("SET search_path TO "+schema);try(var r=s.executeQuery("SELECT available_quantity,reserved_quantity,sold_quantity,initial_quantity,(SELECT count(*) FROM reservation_identity),(SELECT count(*) FROM reservation_request WHERE error_code='INSUFFICIENT_STOCK') FROM catalog_item")){assertThat(r.next()).isTrue();assertThat(r.getLong(1)).isEqualTo(10);assertThat(r.getLong(2)).isZero();assertThat(r.getLong(3)).isZero();assertThat(r.getLong(4)).isEqualTo(10);assertThat(r.getLong(5)).isEqualTo(1);assertThat(r.getLong(6)).isEqualTo(1);}
  }
 }
 @Test void v2UnallocatedSnapshotFailsClosedWithoutPartialUpgrade()throws Exception{
  String schema="ambiguous_upgrade";version(schema,"2").migrate();sql(schema,"INSERT INTO catalog_item VALUES ('AEGIS_ITEM',125,'INR',10); BEGIN; INSERT INTO reservation_request VALUES ('11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222','AEGIS_ITEM',2,'CREATED',NULL,clock_timestamp()); INSERT INTO reservation VALUES ('11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222','AEGIS_ITEM',2,125,250,'INR','ACTIVE','2026-10-25T00:00:00Z','2026-10-25T00:00:30Z'); COMMIT");
  assertThatThrownBy(()->version(schema,null).migrate()).isInstanceOf(org.flywaydb.core.api.FlywayException.class).hasStackTraceContaining("reviewed accounting migration");assertThat(java.util.Arrays.stream(version(schema,"2").info().applied()).filter(m->m.getVersion()!=null).count()).isEqualTo(2);
  try(var c=DriverManager.getConnection(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword());var s=c.createStatement()){s.execute("SET search_path TO "+schema);try(var r=s.executeQuery("SELECT available_quantity,(SELECT count(*) FROM reservation),to_regclass('reservation_identity') FROM catalog_item")){r.next();assertThat(r.getLong(1)).isEqualTo(10);assertThat(r.getLong(2)).isEqualTo(1);assertThat(r.getString(3)).isNull();}}
 }
}
