package io.aegis.payment.application;

import io.aegis.payment.domain.Payment;
import io.aegis.payment.infrastructure.persistence.PaymentRepository;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentServiceTest {
    final PaymentRepository repository = mock(PaymentRepository.class);
    final PaymentService service = new PaymentService(repository);
    @Test void returnsStoredOutcomeWithoutInventingOne() {
        var id = UUID.randomUUID();
        var stored = new Payment(id, UUID.randomUUID(), UUID.randomUUID(), 200, "INR", Payment.State.PENDING, null, Instant.EPOCH, Instant.EPOCH);
        when(repository.findById(id)).thenReturn(Optional.of(stored));
        assertThat(service.find(id.toString())).isSameAs(stored);
    }
    @Test void missingIsNotADecline() {
        var id = UUID.randomUUID(); when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.find(id.toString())).isInstanceOf(PaymentNotFoundException.class);
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"bad", "1-1-1-1-1", " 11111111-1111-4111-8111-111111111111", "11111111111141118111111111111111"})
    void rejectsNonContractUuidBeforeQuery(String id) {
        assertThatThrownBy(() -> service.find(id)).isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(repository);
    }
}
