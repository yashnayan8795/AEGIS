package io.aegis.checkout.infrastructure.persistence;

import io.aegis.checkout.domain.Workflow;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Repository
public class WorkflowRepository {
    private final JdbcClient jdbc;
    public WorkflowRepository(JdbcClient jdbc) { this.jdbc=jdbc; }
    public static class FingerprintConflict extends RuntimeException {}

    /** Atomic storage primitive; no API, remote side effect or workflow advancement. */
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Workflow initialize(UUID key,String sku,int quantity,Workflow.Ids ids) {
        var inserted=jdbc.sql("""
            INSERT INTO purchase_key(scope,client_key,purchase_id,sku,quantity)
            VALUES ('v1:synthetic:purchase',:key,:purchase,:sku,:quantity)
            ON CONFLICT(scope,client_key) DO NOTHING RETURNING purchase_id
            """).param("key",key).param("purchase",ids.purchase()).param("sku",sku).param("quantity",quantity).query(UUID.class).optional();
        if(inserted.isEmpty()) {
            var existing=jdbc.sql("SELECT purchase_id,sku,quantity FROM purchase_key WHERE scope='v1:synthetic:purchase' AND client_key=:key")
                .param("key",key).query((rs,n)->new Binding(rs.getObject(1,UUID.class),rs.getString(2),rs.getInt(3))).single();
            if(!existing.sku().equals(sku)||existing.quantity()!=quantity)throw new FingerprintConflict();
            return find(existing.id()).orElseThrow();
        }
        jdbc.sql("""
            INSERT INTO purchase(purchase_id,sku,quantity,amount_minor,currency,state,step,compensation_pending,payment_outcome_known,created_at,updated_at)
            VALUES (:id,:sku,:quantity,NULL,NULL,'PROCESSING','RESERVATION_PENDING',false,false,clock_timestamp(),clock_timestamp())
            """).param("id",ids.purchase()).param("sku",sku).param("quantity",quantity).update();
        jdbc.sql("""
            INSERT INTO purchase_workflow(purchase_id,reservation_id,payment_id,refund_id,notification_id)
            VALUES (:id,:reservation,:payment,:refund,:notification)
            """).param("id",ids.purchase()).param("reservation",ids.reservation()).param("payment",ids.payment()).param("refund",ids.refund()).param("notification",ids.notification()).update();
        jdbc.sql("INSERT INTO workflow_intent(purchase_id,action,operation_id,sku,quantity) VALUES (:id,'RESERVE',:operation,:sku,:quantity)")
            .param("id",ids.purchase()).param("operation",ids.reservation()).param("sku",sku).param("quantity",quantity).update();
        return find(ids.purchase()).orElseThrow();
    }
    private record Binding(UUID id,String sku,int quantity) {}
    public Optional<Workflow> find(UUID id) { return select(id,false); }
    private Optional<Workflow> select(UUID id,boolean lock) {
        return jdbc.sql("SELECT * FROM purchase_workflow WHERE purchase_id=:id"+(lock?" FOR UPDATE":""))
            .param("id",id).query(WorkflowRepository::map).optional();
    }
    private boolean terminal(UUID id) {
        var state=jdbc.sql("SELECT state FROM purchase WHERE purchase_id=:id").param("id",id).query(String.class).single();
        return state.equals("CONFIRMED")||state.equals("FAILED");
    }
    private Instant now() { return jdbc.sql("SELECT clock_timestamp()").query(OffsetDateTime.class).single().toInstant(); }
    private static Workflow map(ResultSet r,int row)throws SQLException {
        var until=r.getObject("lease_until",OffsetDateTime.class);
        return new Workflow(r.getObject("purchase_id",UUID.class),r.getObject("reservation_id",UUID.class),r.getObject("payment_id",UUID.class),
            r.getObject("refund_id",UUID.class),r.getObject("notification_id",UUID.class),r.getLong("revision"),r.getLong("generation"),
            r.getObject("lease_owner",UUID.class),until==null?null:until.toInstant(),r.getBoolean("dispatch_paused"));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Optional<Workflow> claim(UUID id,UUID owner,int seconds) {
        if(seconds<=0)throw new IllegalArgumentException("positive lease required");
        var value=select(id,true);if(value.isEmpty())return Optional.empty();var row=value.get();var time=now();
        if(terminal(id)||row.dispatchPaused()||(row.leaseUntil()!=null&&row.leaseUntil().isAfter(time)))return Optional.empty();
        jdbc.sql("UPDATE purchase_workflow SET revision=revision+1,generation=generation+1,lease_owner=:owner,lease_until=:until WHERE purchase_id=:id")
            .param("owner",owner).param("until",OffsetDateTime.ofInstant(time.plusSeconds(seconds),ZoneOffset.UTC)).param("id",id).update();
        return find(id);
    }
    /** Persist an observation only under current authority; does not interpret remote evidence or advance Purchase. */
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public boolean observe(UUID id,UUID owner,long generation,long revision,String action,String outcome) {
        var row=select(id,true).orElseThrow();var time=now();
        if(terminal(id)||row.dispatchPaused()||!Objects.equals(row.leaseOwner(),owner)||row.generation()!=generation||row.revision()!=revision||row.leaseUntil()==null||!row.leaseUntil().isAfter(time))return false;
        jdbc.sql("""
            INSERT INTO workflow_observation(purchase_id,action,outcome,observed_at) VALUES (:id,:action,:outcome,:now)
            ON CONFLICT(purchase_id,action) DO UPDATE SET outcome=EXCLUDED.outcome,observed_at=EXCLUDED.observed_at
            """).param("id",id).param("action",action).param("outcome",outcome).param("now",OffsetDateTime.ofInstant(time,ZoneOffset.UTC)).update();
        jdbc.sql("UPDATE purchase_workflow SET revision=revision+1 WHERE purchase_id=:id").param("id",id).update();return true;
    }
}
