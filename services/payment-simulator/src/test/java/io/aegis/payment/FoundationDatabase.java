package io.aegis.payment;
import java.sql.*;
import org.flywaydb.core.Flyway;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;
final class FoundationDatabase {
 static final PostgreSQLContainer DB=new PostgreSQLContainer("postgres:17.11").withDatabaseName("aegis_payment");
 static Connection owner()throws SQLException{return DriverManager.getConnection(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword());}
 static void configure(DynamicPropertyRegistry p)throws SQLException {
  Flyway.configure().dataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()).load().migrate();
  try(var c=owner();var s=c.createStatement()){
   s.execute("CREATE ROLE foundation_writer LOGIN PASSWORD 'test-only-password'");
   s.execute("REVOKE ALL ON DATABASE aegis_payment FROM PUBLIC");s.execute("GRANT CONNECT ON DATABASE aegis_payment TO foundation_writer");
   s.execute("REVOKE ALL ON SCHEMA public FROM PUBLIC");s.execute("GRANT USAGE ON SCHEMA public TO foundation_writer");
   s.execute("GRANT SELECT,INSERT,UPDATE ON TABLE payment,charge_policy,refund TO foundation_writer");
   s.execute("CREATE DATABASE other_service");s.execute("REVOKE ALL ON DATABASE other_service FROM PUBLIC");
  }
  p.add("spring.datasource.url",DB::getJdbcUrl);p.add("spring.datasource.username",()->"foundation_writer");p.add("spring.datasource.password",()->"test-only-password");
  p.add("spring.flyway.url",DB::getJdbcUrl);p.add("spring.flyway.user",DB::getUsername);p.add("spring.flyway.password",DB::getPassword);
 }
 static void sql(String sql)throws SQLException {try(var c=owner();var s=c.createStatement()){s.execute(sql);}}
 private FoundationDatabase(){}
}
