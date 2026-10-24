package io.aegis.inventory.api;
import io.aegis.inventory.application.*;
import io.aegis.inventory.domain.*;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
public class ReservationController {
 private final ReservationService service;
 public ReservationController(ReservationService service){this.service=service;}
 @PutMapping(value="/api/v1/reservations/{reservationId}",consumes="application/json",produces="application/json")
 public ResponseEntity<Reservation> reserve(@PathVariable String reservationId,@RequestBody String body){
  var request=ReservationRequest.parse(body);var result=service.reserve(reservationId,request.purchaseId(),request.sku(),request.quantity());
  requireSuccess(result);
  return result.created()?ResponseEntity.created(URI.create("/api/v1/reservations/"+result.reservation().reservationId())).body(result.reservation()):ResponseEntity.ok(result.reservation());
 }
 @GetMapping(value="/api/v1/reservations/{reservationId}",produces="application/json")
 public Reservation get(@PathVariable String reservationId){return service.find(reservationId);}
 @PutMapping(value="/api/v1/reservations/{reservationId}/commit",produces="application/json")
 public Reservation commit(@PathVariable String reservationId,@RequestBody(required=false) String body){empty(body);return requireSuccess(service.commit(reservationId));}
 @PutMapping(value="/api/v1/reservations/{reservationId}/release",produces="application/json")
 public Reservation release(@PathVariable String reservationId,@RequestBody(required=false) String body){empty(body);return requireSuccess(service.release(reservationId));}
 private void empty(String body){if(body!=null&&!body.isBlank())throw new InvalidReservationRequest();}
 private Reservation requireSuccess(ReservationDecision result){
  // The proxied repository transaction has committed before expected business errors become HTTP.
  if(result.failure()!=null)throw new ReservationBusinessException(result.failure());return result.reservation();
 }
}
