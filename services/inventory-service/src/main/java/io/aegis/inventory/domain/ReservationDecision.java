package io.aegis.inventory.domain;
/** Expected failures are values so rejection/expiry effects commit before HTTP translation. */
public record ReservationDecision(Reservation reservation,boolean created,ReservationFailure failure) {
 public static ReservationDecision error(ReservationFailure failure){return new ReservationDecision(null,false,failure);}
 public static ReservationDecision stored(Reservation value,boolean created){return new ReservationDecision(value,created,null);}
}
