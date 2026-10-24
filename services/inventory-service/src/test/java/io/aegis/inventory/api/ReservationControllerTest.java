package io.aegis.inventory.api;
import io.aegis.inventory.application.*;
import io.aegis.inventory.domain.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(ReservationController.class)
class ReservationControllerTest {
 @Autowired MockMvc mvc;@MockitoBean ReservationService service;
 final UUID id=UUID.randomUUID(),purchase=UUID.randomUUID();final String path="/api/v1/reservations/"+id;
 final Reservation row=new Reservation(id,purchase,"AEGIS_ITEM",2,100,200,"INR",Reservation.State.ACTIVE,Instant.parse("2026-10-25T00:00:00Z"),Instant.parse("2026-10-25T00:00:30Z"));
 String body(){return "{\"purchaseId\":\""+purchase+"\",\"sku\":\"AEGIS_ITEM\",\"quantity\":2}";}
 @Test void newAndReplayStatusesAndLocation()throws Exception{
  when(service.reserve(id.toString(),purchase.toString(),"AEGIS_ITEM",2)).thenReturn(ReservationDecision.stored(row,true),ReservationDecision.stored(row,false));
  mvc.perform(put(path).contentType("application/json").content(body())).andExpect(status().isCreated()).andExpect(header().string("Location",path)).andExpect(jsonPath("$.totalAmountMinor").value(200)).andExpect(jsonPath("$.state").value("ACTIVE"));
  mvc.perform(put(path).contentType("application/json").content(body())).andExpect(status().isOk());
 }
 @ParameterizedTest @CsvSource({"ITEM_NOT_FOUND,404","MONETARY_OVERFLOW,400","INSUFFICIENT_STOCK,409","RESERVATION_ID_REUSED,409","PURCHASE_RESERVATION_EXISTS,409"})
 void durableBusinessOutcomeBecomesContractError(String code,int status)throws Exception{when(service.reserve(anyString(),anyString(),anyString(),anyInt())).thenReturn(ReservationDecision.error(ReservationFailure.valueOf(code)));mvc.perform(put(path).contentType("application/json").content(body())).andExpect(status().is(status)).andExpect(content().contentTypeCompatibleWith("application/problem+json")).andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.instance").value(path));}
 @ParameterizedTest @ValueSource(strings={"{}","null","[]","{","{\"purchaseId\":null,\"sku\":\"AEGIS_ITEM\",\"quantity\":1}","{\"purchaseId\":\"x\",\"sku\":\"AEGIS_ITEM\",\"quantity\":1.5}","{\"purchaseId\":\"x\",\"sku\":\"AEGIS_ITEM\",\"quantity\":2147483648}","{\"purchaseId\":\"x\",\"sku\":\"AEGIS_ITEM\",\"quantity\":2,\"extra\":1}"})
 void invalidJsonNeverCallsService(String body)throws Exception{mvc.perform(put(path).contentType("application/json").content(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));verifyNoInteractions(service);}
 @Test void unsupportedMediaType()throws Exception{mvc.perform(put(path).contentType("text/plain").content(body())).andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));}
 @Test void getAndTerminalCommandErrors()throws Exception{when(service.find(id.toString())).thenReturn(row);mvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.expiresAt").value("2026-10-25T00:00:30Z"));when(service.commit(id.toString())).thenReturn(ReservationDecision.error(ReservationFailure.RESERVATION_EXPIRED));mvc.perform(put(path+"/commit")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESERVATION_EXPIRED"));when(service.release(id.toString())).thenReturn(ReservationDecision.error(ReservationFailure.RESERVATION_COMMITTED));mvc.perform(put(path+"/release")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESERVATION_COMMITTED"));}
 @Test void storageAndUnexpectedErrorsAreSanitized()throws Exception{when(service.find(id.toString())).thenThrow(new DataAccessResourceFailureException("SELECT secret password"));mvc.perform(get(path)).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));doThrow(new IllegalStateException("SELECT secret password")).when(service).find(id.toString());mvc.perform(get(path)).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.detail").value("An unexpected error occurred."));}
 @Test void noCommandPayloadAccepted()throws Exception{mvc.perform(put(path+"/commit").contentType("application/json").content("{}")).andExpect(status().isBadRequest());verifyNoInteractions(service);}
}
