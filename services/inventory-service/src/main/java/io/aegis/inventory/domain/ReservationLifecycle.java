package io.aegis.inventory.domain;
import java.time.Instant;
import static io.aegis.inventory.domain.Reservation.State.*;
import static io.aegis.inventory.domain.ReservationFailure.*;
public final class ReservationLifecycle {
 public enum Action { COMMIT, RELEASE, EXPIRE }
 public record Resolution(Reservation.State state,ReservationFailure failure) {}
 private ReservationLifecycle(){}
 /** The production caller supplies time read from PostgreSQL after all contended locks. */
 public static Resolution decide(Reservation.State current,Action action,Instant decisionTime,Instant expiresAt){
  if(current==COMMITTED)return new Resolution(current,action==Action.RELEASE?RESERVATION_COMMITTED:null);
  if(current==RELEASED)return new Resolution(current,action==Action.COMMIT?RESERVATION_RELEASED:null);
  if(current==EXPIRED)return new Resolution(current,action==Action.COMMIT?RESERVATION_EXPIRED:null);
  if(!decisionTime.isBefore(expiresAt))return new Resolution(EXPIRED,action==Action.COMMIT?RESERVATION_EXPIRED:null);
  return new Resolution(switch(action){case COMMIT->COMMITTED;case RELEASE->RELEASED;case EXPIRE->ACTIVE;},null);
 }
}
