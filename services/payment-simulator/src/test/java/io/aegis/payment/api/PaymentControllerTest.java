package io.aegis.payment.api;

import io.aegis.payment.application.*;
import io.aegis.payment.domain.Payment;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {
    static final String ID="11111111-1111-4111-8111-111111111111";
    @Autowired MockMvc mvc;
    @MockitoBean PaymentService service;
    @Test void requiredNullDeclineCodeIsPresent() throws Exception {
        when(service.find(ID)).thenReturn(new Payment(UUID.fromString(ID), UUID.randomUUID(), UUID.randomUUID(), 200,"INR",Payment.State.SUCCEEDED,null,Instant.EPOCH,Instant.EPOCH));
        mvc.perform(get("/api/v1/payments/"+ID)).andExpect(status().isOk())
            .andExpect(jsonPath("$.amountMinor").value(200)).andExpect(jsonPath("$.currency").value("INR"))
            .andExpect(jsonPath("$.declineCode").value(nullValue())).andExpect(jsonPath("$.state").value("SUCCEEDED"));
    }
    @Test void unknownIs404() throws Exception {
        when(service.find(ID)).thenThrow(new PaymentNotFoundException());
        mvc.perform(get("/api/v1/payments/"+ID)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }
    @Test void invalidIs400() throws Exception {
        when(service.find("bad")).thenThrow(new InvalidRequestException());
        mvc.perform(get("/api/v1/payments/bad")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
    @Test void unavailableDoesNotMeanDeclined() throws Exception {
        when(service.find(ID)).thenThrow(new DataAccessResourceFailureException("private JDBC details"));
        mvc.perform(get("/api/v1/payments/"+ID)).andExpect(status().isServiceUnavailable())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
            .andExpect(jsonPath("$.detail").value("Storage is temporarily unavailable; a previous mutation outcome may be unknown."));
    }
    @Test void unexpectedIsSanitized() throws Exception {
        when(service.find(ID)).thenThrow(new IllegalStateException("SELECT password"));
        mvc.perform(get("/api/v1/payments/"+ID)).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
    }
    @Test void noChargeOrRefundHandler() throws Exception {
        mvc.perform(put("/api/v1/payments/"+ID).contentType("application/json").content("{}"))
            .andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/api/v1/payments/"+ID+"/refunds/"+ID)).andExpect(status().isNotFound());
    }
}
