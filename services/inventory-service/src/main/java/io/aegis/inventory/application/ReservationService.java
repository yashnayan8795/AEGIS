package io.aegis.inventory.application;
import io.aegis.inventory.domain.*;
import io.aegis.inventory.infrastructure.persistence.ReservationRepository;
import io.aegis.inventory.infrastructure.persistence.ReservationTransactions;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
@Service
public class ReservationService {
 private static final Pattern UUID_TEXT=Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
 private static final Pattern SKU=Pattern.compile("[A-Z0-9][A-Z0-9_-]{0,63}");
 private final ReservationTransactions transactions; private final ReservationRepository repository;
 public ReservationService(ReservationTransactions transactions,ReservationRepository repository){this.transactions=transactions;this.repository=repository;}
 private UUID id(String value){if(value==null||!UUID_TEXT.matcher(value).matches())throw new InvalidReservationRequest();return UUID.fromString(value);}
 public ReservationDecision reserve(String reservation,String purchase,String sku,int quantity){
  var reservationId=id(reservation);var purchaseId=id(purchase);
  if(sku==null||!SKU.matcher(sku).matches()||quantity<=0)throw new InvalidReservationRequest();
  return transactions.reserve(reservationId,purchaseId,sku,quantity);
 }
 public Reservation find(String value){return repository.find(id(value)).orElseThrow(()->new ReservationBusinessException(ReservationFailure.RESERVATION_NOT_FOUND));}
 public ReservationDecision commit(String value){return transactions.transition(id(value),ReservationLifecycle.Action.COMMIT);}
 public ReservationDecision release(String value){return transactions.transition(id(value),ReservationLifecycle.Action.RELEASE);}
}
