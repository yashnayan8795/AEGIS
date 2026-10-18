package io.aegis.notification.application;

import io.aegis.notification.domain.NotificationReceipt;
import io.aegis.notification.infrastructure.persistence.NotificationRepository;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    final NotificationRepository repository=mock(NotificationRepository.class);
    final NotificationService service=new NotificationService(repository);
    final UUID id=UUID.randomUUID(),purchase=UUID.randomUUID();
    NotificationReceipt receipt(UUID p){return new NotificationReceipt(id,p,"PURCHASE_CONFIRMED","STORED",Instant.EPOCH);}
    @Test void newReceipt() {
        when(repository.insertIfAbsent(id,purchase,"PURCHASE_CONFIRMED")).thenReturn(Optional.of(receipt(purchase)));
        assertThat(service.store(id.toString(),purchase.toString(),"PURCHASE_CONFIRMED").created()).isTrue();
        verify(repository,never()).findById(any());
    }
    @Test void matchingReplayKeepsOriginalReceipt() {
        var stored=receipt(purchase);when(repository.insertIfAbsent(id,purchase,"PURCHASE_CONFIRMED")).thenReturn(Optional.empty());when(repository.findById(id)).thenReturn(Optional.of(stored));
        var result=service.store(id.toString(),purchase.toString(),"PURCHASE_CONFIRMED");assertThat(result.created()).isFalse();assertThat(result.receipt()).isSameAs(stored);
    }
    @Test void conflictCannotOverwrite() {
        when(repository.insertIfAbsent(id,purchase,"PURCHASE_CONFIRMED")).thenReturn(Optional.empty());when(repository.findById(id)).thenReturn(Optional.of(receipt(UUID.randomUUID())));
        assertThatThrownBy(()->service.store(id.toString(),purchase.toString(),"PURCHASE_CONFIRMED")).isInstanceOf(NotificationConflictException.class);
    }
    @ParameterizedTest @ValueSource(strings={"bad","1-1-1-1-1",""})
    void invalidUuidBeforeSql(String invalid) {
        assertThatThrownBy(()->service.store(invalid,purchase.toString(),"PURCHASE_CONFIRMED")).isInstanceOf(InvalidRequestException.class);verifyNoInteractions(repository);
    }
    @Test void kindMustMatchContract() {
        assertThatThrownBy(()->service.store(id.toString(),purchase.toString(),"EMAIL_SENT")).isInstanceOf(InvalidRequestException.class);verifyNoInteractions(repository);
    }
    @Test void missingIsExplicit() {
        when(repository.findById(id)).thenReturn(Optional.empty());assertThatThrownBy(()->service.find(id.toString())).isInstanceOf(NotificationNotFoundException.class);
    }
}
