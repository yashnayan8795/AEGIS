package io.aegis.inventory.domain;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;
import static io.aegis.inventory.domain.Reservation.State.*;
import static io.aegis.inventory.domain.ReservationLifecycle.Action.*;
class ReservationLifecycleTest {
 static Stream<Arguments> transitions(){return Stream.of(
  Arguments.of(ACTIVE,COMMIT,-1,COMMITTED,null),Arguments.of(ACTIVE,RELEASE,-1,RELEASED,null),Arguments.of(ACTIVE,EXPIRE,-1,ACTIVE,null),
  Arguments.of(ACTIVE,COMMIT,0,EXPIRED,ReservationFailure.RESERVATION_EXPIRED),Arguments.of(ACTIVE,COMMIT,1,EXPIRED,ReservationFailure.RESERVATION_EXPIRED),
  Arguments.of(ACTIVE,RELEASE,0,EXPIRED,null),Arguments.of(ACTIVE,EXPIRE,0,EXPIRED,null),
  Arguments.of(COMMITTED,COMMIT,1,COMMITTED,null),Arguments.of(COMMITTED,RELEASE,1,COMMITTED,ReservationFailure.RESERVATION_COMMITTED),Arguments.of(COMMITTED,EXPIRE,1,COMMITTED,null),
  Arguments.of(RELEASED,COMMIT,-1,RELEASED,ReservationFailure.RESERVATION_RELEASED),Arguments.of(RELEASED,RELEASE,1,RELEASED,null),Arguments.of(RELEASED,EXPIRE,1,RELEASED,null),
  Arguments.of(EXPIRED,COMMIT,-1,EXPIRED,ReservationFailure.RESERVATION_EXPIRED),Arguments.of(EXPIRED,RELEASE,1,EXPIRED,null),Arguments.of(EXPIRED,EXPIRE,1,EXPIRED,null));}
 @ParameterizedTest @MethodSource("transitions")
 void immutableTerminalStatesAndStrictDeadline(Reservation.State before,ReservationLifecycle.Action action,int offset,Reservation.State after,ReservationFailure failure){
  var deadline=Instant.parse("2026-10-25T00:00:00Z");
  assertThat(ReservationLifecycle.decide(before,action,deadline.plusNanos(offset),deadline)).isEqualTo(new ReservationLifecycle.Resolution(after,failure));
 }
}
