package io.aegis.checkout.api;

import io.aegis.checkout.application.*;
import io.aegis.checkout.domain.Purchase;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PurchaseController.class)
class PurchaseControllerTest {
    static final String ID="11111111-1111-4111-8111-111111111111";
    @Autowired MockMvc mvc;
    @MockitoBean PurchaseService service;
    @Test void unknownPriceRemainsExplicitNull() throws Exception {
        when(service.find(ID)).thenReturn(new Purchase(UUID.fromString(ID),"AEGIS_ITEM",2,null,null,
            Purchase.State.PROCESSING,Purchase.Step.RESERVATION_PENDING,false,false,Instant.EPOCH,Instant.EPOCH));
        mvc.perform(get("/api/v1/purchases/"+ID)).andExpect(status().isOk())
            .andExpect(jsonPath("$.amountMinor").value(nullValue())).andExpect(jsonPath("$.currency").value(nullValue()))
            .andExpect(jsonPath("$.state").value("PROCESSING")).andExpect(jsonPath("$.step").value("RESERVATION_PENDING"))
            .andExpect(jsonPath("$.compensationPending").value(false)).andExpect(jsonPath("$.paymentOutcomeKnown").value(false));
    }
    @Test void unknownIs404() throws Exception {
        when(service.find(ID)).thenThrow(new PurchaseNotFoundException());
        mvc.perform(get("/api/v1/purchases/"+ID)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PURCHASE_NOT_FOUND"));
    }
    @Test void invalidIs400() throws Exception {
        when(service.find("bad")).thenThrow(new InvalidRequestException());
        mvc.perform(get("/api/v1/purchases/bad")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
    @Test void unavailableIsSanitized503() throws Exception {
        when(service.find(ID)).thenThrow(new DataAccessResourceFailureException("jdbc:postgresql password=secret"));
        mvc.perform(get("/api/v1/purchases/"+ID)).andExpect(status().isServiceUnavailable())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.type").value("about:blank")).andExpect(jsonPath("$.title").value("Service Unavailable"))
            .andExpect(jsonPath("$.status").value(503)).andExpect(jsonPath("$.instance").value("/api/v1/purchases/"+ID))
            .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
            .andExpect(jsonPath("$.detail").value("Storage is temporarily unavailable; a previous mutation outcome may be unknown."));
    }
    @Test void queryTimeoutIs503() throws Exception {
        when(service.find(ID)).thenThrow(new QueryTimeoutException("private SQL"));
        mvc.perform(get("/api/v1/purchases/"+ID)).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }
    @Test void unexpectedIsSanitized500() throws Exception {
        when(service.find(ID)).thenThrow(new IllegalStateException("SELECT password"));
        mvc.perform(get("/api/v1/purchases/"+ID)).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500)).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
    }
    @Test void sqlProgrammingErrorIs500RatherThanOutage() throws Exception {
        when(service.find(ID)).thenThrow(new org.springframework.jdbc.BadSqlGrammarException("private SQL","private SQL",new java.sql.SQLException("secret")));
        mvc.perform(get("/api/v1/purchases/"+ID)).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }
    @Test void noSubmissionHandler() throws Exception {
        mvc.perform(post("/api/v1/purchases").contentType("application/json").content("{}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/purchases/"+ID).contentType("application/json").content("{}")).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }
}
