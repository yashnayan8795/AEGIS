package io.aegis.inventory.application;
import io.aegis.inventory.domain.ReservationFailure;
public class ReservationBusinessException extends RuntimeException {
 private final ReservationFailure failure;
 public ReservationBusinessException(ReservationFailure failure){this.failure=failure;}
 public ReservationFailure failure(){return failure;}
}
