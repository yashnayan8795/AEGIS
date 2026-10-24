package io.aegis.payment.infrastructure.persistence;

import io.aegis.payment.domain.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Repository
public class PaymentFoundationRepository {
    private final JdbcClient jdbc;
    private final PaymentRepository payments;
    public PaymentFoundationRepository(JdbcClient jdbc,PaymentRepository payments){this.jdbc=jdbc;this.payments=payments;}
    public static class PayloadConflict extends RuntimeException {}
    public Optional<SyntheticPolicy> policy(UUID id) {
        return jdbc.sql("SELECT * FROM charge_policy WHERE payment_id=:id").param("id",id)
            .query((r,n)->new SyntheticPolicy(r.getString("policy_version"),r.getString("scenario"),r.getString("chosen_outcome"))).optional();
    }
    /** Storage admission only. A later executor must resolve within its enclosing local transaction. */
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Payment admitCharge(UUID id,UUID purchase,UUID reservation,long amount,String currency,SyntheticPolicy policy) {
        var inserted=jdbc.sql("""
            WITH moment AS MATERIALIZED (SELECT clock_timestamp() AS at)
            INSERT INTO payment(payment_id,purchase_id,reservation_id,amount_minor,currency,state,decline_code,created_at,updated_at)
            SELECT :id,:purchase,:reservation,:amount,:currency,'PENDING',NULL,at,at FROM moment
            ON CONFLICT(payment_id) DO NOTHING RETURNING payment_id
            """).param("id",id).param("purchase",purchase).param("reservation",reservation).param("amount",amount).param("currency",currency).query(UUID.class).optional();
        if(inserted.isPresent()) {
            jdbc.sql("INSERT INTO charge_policy(payment_id,policy_version,scenario,chosen_outcome) VALUES (:id,:version,:scenario,:outcome)")
                .param("id",id).param("version",policy.version()).param("scenario",policy.scenario()).param("outcome",policy.chosenOutcome()).update();
        }
        var row=payments.findById(id).orElseThrow();
        if(!row.purchaseId().equals(purchase)||!row.reservationId().equals(reservation)||row.amountMinor()!=amount||!row.currency().equals(currency))throw new PayloadConflict();
        if(row.state()==Payment.State.PENDING&&policy(id).isEmpty())throw new IllegalStateException("Unresolved legacy policy requires investigation");
        return row; // Replays never choose new policy or execute a charge.
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public RefundRecord admitRefund(UUID id,UUID payment,long amount,String currency,String reason,SyntheticPolicy policy) {
        jdbc.sql("""
            WITH moment AS MATERIALIZED (SELECT clock_timestamp() AS at)
            INSERT INTO refund(refund_id,payment_id,amount_minor,currency,reason_code,state,policy_version,scenario,chosen_outcome,created_at,updated_at)
            SELECT :id,:payment,:amount,:currency,:reason,'PENDING',:version,:scenario,:outcome,at,at FROM moment
            ON CONFLICT(refund_id) DO NOTHING
            """).param("id",id).param("payment",payment).param("amount",amount).param("currency",currency).param("reason",reason)
            .param("version",policy.version()).param("scenario",policy.scenario()).param("outcome",policy.chosenOutcome()).update();
        var row=refund(id).orElseThrow();
        if(!row.paymentId().equals(payment)||row.amountMinor()!=amount||!row.currency().equals(currency)||!row.reasonCode().equals(reason))throw new PayloadConflict();
        return row; // No refund execution or terminal update in this checkpoint.
    }
    public Optional<RefundRecord> refund(UUID id) {
        return jdbc.sql("SELECT * FROM refund WHERE refund_id=:id").param("id",id).query((r,n)->new RefundRecord(
            r.getObject("refund_id",UUID.class),r.getObject("payment_id",UUID.class),r.getLong("amount_minor"),r.getString("currency"),r.getString("reason_code"),
            RefundRecord.State.valueOf(r.getString("state")),new SyntheticPolicy(r.getString("policy_version"),r.getString("scenario"),r.getString("chosen_outcome")),
            r.getObject("created_at",OffsetDateTime.class).toInstant(),r.getObject("updated_at",OffsetDateTime.class).toInstant())).optional();
    }
}
