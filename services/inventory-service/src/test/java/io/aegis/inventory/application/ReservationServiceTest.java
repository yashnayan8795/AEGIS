package io.aegis.inventory.application;
import io.aegis.inventory.infrastructure.persistence.*;
import io.aegis.inventory.config.ReservationSettings;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ReservationServiceTest {
 final ReservationTransactions tx=mock(ReservationTransactions.class);final ReservationRepository repo=mock(ReservationRepository.class);
 final ReservationService service=new ReservationService(tx,repo);
 @ParameterizedTest @ValueSource(strings={"bad","1-1-1-1-1","00000000-0000-0000-0000-00000000000Z"})
 void invalidIdentitiesNeverReachDatabase(String id){assertThatThrownBy(()->service.find(id)).isInstanceOf(InvalidReservationRequest.class);assertThatThrownBy(()->service.commit(id)).isInstanceOf(InvalidReservationRequest.class);assertThatThrownBy(()->service.release(id)).isInstanceOf(InvalidReservationRequest.class);verifyNoInteractions(tx,repo);}
 @ParameterizedTest @ValueSource(strings={"bad sku","lowercase","","_SKU"})
 void invalidSkuNeverReachesDatabase(String sku){assertThatThrownBy(()->service.reserve(UUID.randomUUID().toString(),UUID.randomUUID().toString(),sku,1)).isInstanceOf(InvalidReservationRequest.class);verifyNoInteractions(tx,repo);}
 @Test void quantityAndPurchaseIdentityAreValidated(){var id=UUID.randomUUID().toString();assertThatThrownBy(()->service.reserve(id,id,"SKU",0)).isInstanceOf(InvalidReservationRequest.class);assertThatThrownBy(()->service.reserve(id,"bad","SKU",1)).isInstanceOf(InvalidReservationRequest.class);verifyNoInteractions(tx,repo);}
 @Test void missingReservationHasBusinessCode(){var id=UUID.randomUUID();when(repo.find(id)).thenReturn(Optional.empty());assertThatThrownBy(()->service.find(id.toString())).isInstanceOf(ReservationBusinessException.class).extracting("failure").isEqualTo(io.aegis.inventory.domain.ReservationFailure.RESERVATION_NOT_FOUND);}
 @Test void ttlMustBePositive(){assertThatThrownBy(()->new ReservationSettings(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->new ReservationSettings(Duration.ofSeconds(-1))).isInstanceOf(IllegalArgumentException.class);}
}
