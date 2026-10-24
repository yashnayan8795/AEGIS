package io.aegis.inventory.api;
import io.aegis.inventory.application.InvalidReservationRequest;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
/** Local strict reader preserves decimal precision and accepts exactly integral int32 JSON numbers. */
public record ReservationRequest(String purchaseId,String sku,int quantity){
 private static final ObjectMapper JSON=JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
 public static ReservationRequest parse(String body){
  try{
   var value=JSON.readTree(body);
   if(!value.isObject()||value.size()!=3||!value.has("purchaseId")||!value.has("sku")||!value.has("quantity")
      ||!value.get("purchaseId").isString()||!value.get("sku").isString()||!value.get("quantity").isNumber())throw new InvalidReservationRequest();
   return new ReservationRequest(value.get("purchaseId").asString(),value.get("sku").asString(),value.get("quantity").decimalValue().intValueExact());
  }catch(Exception invalid){throw new InvalidReservationRequest();}
 }
}
