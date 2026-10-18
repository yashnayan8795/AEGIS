package io.aegis.inventory.application;

import io.aegis.inventory.domain.CatalogItem;
import io.aegis.inventory.infrastructure.persistence.CatalogRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogServiceTest {
    private final CatalogRepository repository = mock(CatalogRepository.class);
    private final CatalogService service = new CatalogService(repository);

    @Test void returnsAuthoritativeRepositoryData() {
        var item = new CatalogItem("AEGIS_ITEM", 100, "INR", 10);
        when(repository.findBySku("AEGIS_ITEM")).thenReturn(Optional.of(item));
        assertThat(service.find("AEGIS_ITEM")).isEqualTo(item);
    }
    @Test void missingItemIsExplicit() {
        when(repository.findBySku("UNKNOWN")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.find("UNKNOWN")).isInstanceOf(ItemNotFoundException.class);
    }
    @ParameterizedTest
    @ValueSource(strings = {"", "bad", "_BAD", "A B", "A'OR'1", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    void rejectsInvalidInputBeforeDatabaseAccess(String sku) {
        assertThatThrownBy(() -> service.find(sku)).isInstanceOf(InvalidSkuException.class);
        verifyNoInteractions(repository);
    }
    @Test void rejectsNullBeforeDatabaseAccess() {
        assertThatThrownBy(() -> service.find(null)).isInstanceOf(InvalidSkuException.class);
        verifyNoInteractions(repository);
    }
    @Test void acceptsMaximumLengthSku() {
        String sku = "A".repeat(64);
        when(repository.findBySku(sku)).thenReturn(Optional.of(new CatalogItem(sku, 1, "INR", 0)));
        assertThat(service.find(sku).sku()).hasSize(64);
    }
}
