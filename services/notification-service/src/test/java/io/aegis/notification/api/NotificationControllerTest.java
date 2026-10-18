package io.aegis.notification.api;

import io.aegis.notification.application.*;
import io.aegis.notification.domain.NotificationReceipt;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.TransactionSystemException;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {
    static final String ID="11111111-1111-4111-8111-111111111111",PURCHASE="22222222-2222-4222-8222-222222222222";
    static final String BODY="{\"notificationId\":\""+ID+"\",\"purchaseId\":\""+PURCHASE+"\",\"kind\":\"PURCHASE_CONFIRMED\"}";
    @Autowired MockMvc mvc;
    @MockitoBean NotificationService service;
    NotificationReceipt receipt(){return new NotificationReceipt(UUID.fromString(ID),UUID.fromString(PURCHASE),"PURCHASE_CONFIRMED","STORED",Instant.EPOCH);}
    @Test void newReceiptHasLocation()throws Exception {
        when(service.store(ID,PURCHASE,"PURCHASE_CONFIRMED")).thenReturn(new NotificationService.Stored(receipt(),true));
        mvc.perform(post("/api/v1/notifications").contentType("application/json").content(BODY))
            .andExpect(status().isCreated()).andExpect(header().string("Location","/api/v1/notifications/"+ID))
            .andExpect(jsonPath("$.state").value("STORED")).andExpect(jsonPath("$.createdAt").value("1970-01-01T00:00:00Z"));
    }
    @Test void replayHasOriginalTimestamp()throws Exception {
        when(service.store(ID,PURCHASE,"PURCHASE_CONFIRMED")).thenReturn(new NotificationService.Stored(receipt(),false));
        mvc.perform(post("/api/v1/notifications").contentType("application/json").content(BODY))
            .andExpect(status().isOk()).andExpect(jsonPath("$.createdAt").value("1970-01-01T00:00:00Z"));
    }
    @Test void conflictUsesBusinessCode()throws Exception {
        when(service.store(ID,PURCHASE,"PURCHASE_CONFIRMED")).thenThrow(new NotificationConflictException());
        mvc.perform(post("/api/v1/notifications").contentType("application/json").content(BODY))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOTIFICATION_ID_REUSED"));
    }
    @ParameterizedTest @ValueSource(strings={"{}","null","[]","{\"notificationId\":1,\"purchaseId\":\"x\",\"kind\":\"PURCHASE_CONFIRMED\"}","{\"notificationId\":null,\"purchaseId\":\"x\",\"kind\":\"PURCHASE_CONFIRMED\"}","{\"notificationId\":\"x\",\"purchaseId\":\"x\",\"kind\":\"PURCHASE_CONFIRMED\",\"extra\":true}","{"})
    void badJsonShapeIs400(String body)throws Exception {
        mvc.perform(post("/api/v1/notifications").contentType("application/json").content(body))
            .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));verifyNoInteractions(service);
    }
    @Test void unsupportedMediaIs415()throws Exception {
        mvc.perform(post("/api/v1/notifications").contentType("text/plain").content(BODY))
            .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }
    @Test void unknownIs404()throws Exception {
        when(service.find(ID)).thenThrow(new NotificationNotFoundException());
        mvc.perform(get("/api/v1/notifications/"+ID)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }
    @Test void invalidUuidIs400()throws Exception {
        when(service.find("bad")).thenThrow(new InvalidRequestException());
        mvc.perform(get("/api/v1/notifications/bad")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
    @Test void unavailableGetIsSanitized()throws Exception {
        when(service.find(ID)).thenThrow(new DataAccessResourceFailureException("secret jdbc password"));
        mvc.perform(get("/api/v1/notifications/"+ID)).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }
    @Test void failedCommitCannotReturnSuccess()throws Exception {
        when(service.store(ID,PURCHASE,"PURCHASE_CONFIRMED")).thenThrow(new TransactionSystemException("unknown commit result"));
        mvc.perform(post("/api/v1/notifications").contentType("application/json").content(BODY))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }
    @Test void unexpectedIsSanitized()throws Exception {
        when(service.find(ID)).thenThrow(new IllegalStateException("secret database details"));
        mvc.perform(get("/api/v1/notifications/"+ID)).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
    }
}
