package io.aegis.inventory.infrastructure.persistence;

import io.aegis.inventory.config.ReservationSettings;
import io.aegis.inventory.domain.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import static io.aegis.inventory.domain.ReservationFailure.*;

@Repository
public class ReservationTransactions {
 private final JdbcClient jdbc;private final ReservationRepository reservations;private final ReservationSettings settings;
 public ReservationTransactions(JdbcClient jdbc,ReservationRepository reservations,ReservationSettings settings){this.jdbc=jdbc;this.reservations=reservations;this.settings=settings;}
 private record Identity(UUID reservation,UUID purchase,String sku,int quantity){}
 private record Stock(long price,String currency,long available){}
 private Instant now(){return jdbc.sql("SELECT clock_timestamp()").query(OffsetDateTime.class).single().toInstant();}
 @Transactional(isolation=Isolation.READ_COMMITTED)
 public ReservationDecision reserve(UUID id,UUID purchase,String sku,int quantity){
  var inserted=jdbc.sql("""
   INSERT INTO reservation_identity(reservation_id,purchase_id,sku,quantity) VALUES (:id,:purchase,:sku,:quantity)
   ON CONFLICT DO NOTHING RETURNING reservation_id
   """).param("id",id).param("purchase",purchase).param("sku",sku).param("quantity",quantity).query(UUID.class).optional();
  if(inserted.isEmpty()){
   var binding=jdbc.sql("SELECT * FROM reservation_identity WHERE reservation_id=:id").param("id",id).query((r,n)->new Identity(r.getObject("reservation_id",UUID.class),r.getObject("purchase_id",UUID.class),r.getString("sku"),r.getInt("quantity"))).optional();
   if(binding.isEmpty())return ReservationDecision.error(PURCHASE_RESERVATION_EXISTS);
   var row=binding.get();if(!row.purchase().equals(purchase)||!row.sku().equals(sku)||row.quantity()!=quantity)return ReservationDecision.error(RESERVATION_ID_REUSED);
   var result=reservations.request(id).orElseThrow();
   if(result.outcome().equals("REJECTED"))return ReservationDecision.error(ReservationFailure.valueOf(result.errorCode()));
   return ReservationDecision.stored(reservations.find(id).orElseThrow(),false);
  }
  var stock=jdbc.sql("SELECT unit_price_minor,currency,available_quantity FROM catalog_item WHERE sku=:sku FOR UPDATE").param("sku",sku)
    .query((r,n)->new Stock(r.getLong(1),r.getString(2),r.getLong(3))).optional();
  if(stock.isEmpty())return reject(id,purchase,sku,quantity,ITEM_NOT_FOUND);
  long total;try{total=Math.multiplyExact(stock.get().price(),(long)quantity);}catch(ArithmeticException overflow){return reject(id,purchase,sku,quantity,MONETARY_OVERFLOW);}
  if(stock.get().available()<quantity)return reject(id,purchase,sku,quantity,INSUFFICIENT_STOCK);
  var created=now();var expires=created.plus(settings.ttl());
  int moved=jdbc.sql("UPDATE catalog_item SET available_quantity=available_quantity-:quantity,reserved_quantity=reserved_quantity+:quantity WHERE sku=:sku AND available_quantity>=:quantity")
    .param("quantity",quantity).param("sku",sku).update();
  if(moved!=1)throw new IllegalStateException("locked stock changed unexpectedly");
  jdbc.sql("INSERT INTO reservation_request(reservation_id,purchase_id,sku,quantity,outcome) VALUES (:id,:purchase,:sku,:quantity,'CREATED')")
    .param("id",id).param("purchase",purchase).param("sku",sku).param("quantity",quantity).update();
  jdbc.sql("""
   INSERT INTO reservation(reservation_id,purchase_id,sku,quantity,unit_price_minor,total_amount_minor,currency,state,created_at,expires_at)
   VALUES (:id,:purchase,:sku,:quantity,:unit,:total,:currency,'ACTIVE',:created,:expires)
   """).param("id",id).param("purchase",purchase).param("sku",sku).param("quantity",quantity).param("unit",stock.get().price()).param("total",total)
   .param("currency",stock.get().currency()).param("created",OffsetDateTime.ofInstant(created,ZoneOffset.UTC)).param("expires",OffsetDateTime.ofInstant(expires,ZoneOffset.UTC)).update();
  return ReservationDecision.stored(reservations.find(id).orElseThrow(),true);
 }
 private ReservationDecision reject(UUID id,UUID purchase,String sku,int quantity,ReservationFailure failure){
  reservations.retainRejection(id,purchase,sku,quantity,failure.name());return ReservationDecision.error(failure);
 }
 @Transactional(isolation=Isolation.READ_COMMITTED)
 public ReservationDecision transition(UUID id,ReservationLifecycle.Action action){
  var locked=jdbc.sql("SELECT reservation_id FROM reservation WHERE reservation_id=:id FOR UPDATE").param("id",id).query(UUID.class).optional();
  if(locked.isEmpty())return ReservationDecision.error(RESERVATION_NOT_FOUND);
  var row=reservations.find(id).orElseThrow();
  if(row.state()==Reservation.State.ACTIVE){
   jdbc.sql("SELECT sku FROM catalog_item WHERE sku=:sku FOR UPDATE").param("sku",row.sku()).query(String.class).single();
  }
  // Fresh decision time only after reservation and catalog contention has resolved.
  var resolution=ReservationLifecycle.decide(row.state(),action,now(),row.expiresAt());
  if(resolution.state()!=row.state()){
   String counters=resolution.state()==Reservation.State.COMMITTED
     ?"reserved_quantity=reserved_quantity-:quantity,sold_quantity=sold_quantity+:quantity"
     :"reserved_quantity=reserved_quantity-:quantity,available_quantity=available_quantity+:quantity";
   int moved=jdbc.sql("UPDATE catalog_item SET "+counters+" WHERE sku=:sku AND reserved_quantity>=:quantity").param("quantity",row.quantity()).param("sku",row.sku()).update();
   if(moved!=1)throw new IllegalStateException("reserved stock missing");
   int changed=jdbc.sql("UPDATE reservation SET state=:state WHERE reservation_id=:id AND state='ACTIVE'").param("state",resolution.state().name()).param("id",id).update();
   if(changed!=1)throw new IllegalStateException("locked reservation changed unexpectedly");
  }
  return new ReservationDecision(reservations.find(id).orElseThrow(),false,resolution.failure());
 }
 /** Guarded expiry primitive for future scheduling; C registers no cleanup worker or endpoint. */
 @Transactional(isolation=Isolation.READ_COMMITTED)
 public ReservationDecision expireIfDue(UUID id){return transition(id,ReservationLifecycle.Action.EXPIRE);}
}
