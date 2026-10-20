package io.aegis.checkout.application;

import io.aegis.checkout.domain.Purchase;
import io.aegis.checkout.infrastructure.persistence.PurchaseRepository;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PurchaseServiceTest {
    final PurchaseRepository repository = mock(PurchaseRepository.class);
    final PurchaseService service = new PurchaseService(repository);
    @Test void returnsStoredSnapshotWithoutAdvancement() {
        var id = UUID.randomUUID();
        var stored = new Purchase(id, "AEGIS_ITEM", 2, null, null, Purchase.State.PROCESSING, Purchase.Step.RESERVATION_PENDING, false, false, Instant.EPOCH, Instant.EPOCH);
        when(repository.findById(id)).thenReturn(Optional.of(stored));
        assertThat(service.find(id.toString())).isSameAs(stored);
    }
    @Test void missingIsNotAFailedPurchase() {
        var id = UUID.randomUUID(); when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.find(id.toString())).isInstanceOf(PurchaseNotFoundException.class);
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"bad", "1-1-1-1-1", " 11111111-1111-4111-8111-111111111111", "11111111111141118111111111111111"})
    void rejectsNonContractUuidBeforeQuery(String id) {
        assertThatThrownBy(() -> service.find(id)).isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(repository);
    }
}
