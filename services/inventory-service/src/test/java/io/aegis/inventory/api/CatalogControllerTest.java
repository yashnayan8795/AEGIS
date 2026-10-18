package io.aegis.inventory.api;

import io.aegis.inventory.application.*;
import io.aegis.inventory.domain.CatalogItem;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CatalogController.class)
class CatalogControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CatalogService service;
    @MockitoBean Clock clock;
    @BeforeEach void setClock() { when(clock.instant()).thenReturn(Instant.parse("2026-10-18T00:00:00Z")); }

    @Test void serializesCatalogSnapshot() throws Exception {
        when(service.find("AEGIS_ITEM")).thenReturn(new CatalogItem("AEGIS_ITEM", 100, "INR", 10));
        mvc.perform(get("/api/v1/items/AEGIS_ITEM")).andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.sku").value("AEGIS_ITEM"))
            .andExpect(jsonPath("$.unitPriceMinor").value(100))
            .andExpect(jsonPath("$.currency").value("INR"))
            .andExpect(jsonPath("$.availableQuantity").value(10))
            .andExpect(jsonPath("$.observedAt").value("2026-10-18T00:00:00Z"));
    }
    @Test void unknownSkuHasContractError() throws Exception {
        when(service.find("UNKNOWN")).thenThrow(new ItemNotFoundException());
        mvc.perform(get("/api/v1/items/UNKNOWN")).andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.code").value("ITEM_NOT_FOUND"))
            .andExpect(jsonPath("$.status").value(404));
    }
    @Test void invalidSkuHasContractError() throws Exception {
        when(service.find("bad")).thenThrow(new InvalidSkuException());
        mvc.perform(get("/api/v1/items/bad")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.instance").value("/api/v1/items/bad"));
    }
    @Test void unavailableStorageDoesNotLeakDetails() throws Exception {
        when(service.find("AEGIS_ITEM")).thenThrow(new DataAccessResourceFailureException("secret jdbc:postgresql://private user password"));
        mvc.perform(get("/api/v1/items/AEGIS_ITEM")).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
            .andExpect(jsonPath("$.detail").value("Catalog storage is temporarily unavailable."));
    }
    @Test void unexpectedFailureDoesNotLeakDetails() throws Exception {
        when(service.find("AEGIS_ITEM")).thenThrow(new IllegalStateException("SELECT secret FROM internal"));
        mvc.perform(get("/api/v1/items/AEGIS_ITEM")).andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
    }
    @Test void noReservationHandlerIsRegistered() throws Exception {
        mvc.perform(get("/api/v1/reservations/11111111-1111-4111-8111-111111111111"))
            .andExpect(status().isNotFound());
    }
}
