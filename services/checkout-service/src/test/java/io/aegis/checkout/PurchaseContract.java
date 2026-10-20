package io.aegis.checkout;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

/** Test-only evaluator for the keywords used by Purchase, not a full OpenAPI validator. */
final class PurchaseContract {
    static final ObjectMapper JSON = JsonMapper.builder().build();
    static final JsonNode DOCUMENT = JSON.readTree(Path.of("../../contracts/checkout-v1.json").toFile());
    static final JsonNode SCHEMA = DOCUMENT.path("components").path("schemas").path("Purchase");
    private PurchaseContract() {}

    static boolean matches(JsonNode schema, JsonNode value) {
        if (schema.has("const") && !schema.get("const").equals(value)) return false;
        if (schema.has("enum")) {
            boolean found=false; for(var choice:schema.get("enum")) if(choice.equals(value)) found=true;
            if(!found) return false;
        }
        if(schema.has("type")) {
            var types=schema.get("type"); boolean found=false;
            if(types.isArray()) {for(var type:types) if(typeMatches(type.asString(),value)) found=true;}
            else found=typeMatches(types.asString(),value);
            if(!found) return false;
        }
        if(value.isObject()) {
            for(var key:schema.path("required")) if(!value.has(key.asString())) return false;
            for(var entry:schema.path("properties").properties())
                if(value.has(entry.getKey()) && !matches(entry.getValue(),value.get(entry.getKey()))) return false;
            if(schema.has("additionalProperties") && schema.get("additionalProperties").isBoolean()
                    && !schema.get("additionalProperties").asBoolean())
                for(var entry:value.properties()) if(!schema.path("properties").has(entry.getKey())) return false;
        }
        if(value.isString()) {
            var text=value.asString();
            if(schema.has("minLength") && text.length()<schema.get("minLength").asInt()) return false;
            if(schema.has("maxLength") && text.length()>schema.get("maxLength").asInt()) return false;
            if(schema.has("pattern") && !text.matches(schema.get("pattern").asString())) return false;
            if(schema.path("format").asString("").equals("date-time")) {
                try {Instant.parse(text);} catch(java.time.format.DateTimeParseException ex) {return false;}
            }
        }
        if(value.isNumber()) {
            if(schema.has("minimum") && value.decimalValue().compareTo(schema.get("minimum").decimalValue())<0) return false;
            if(schema.has("maximum") && value.decimalValue().compareTo(schema.get("maximum").decimalValue())>0) return false;
        }
        if(schema.has("oneOf")) {int count=0;for(var branch:schema.get("oneOf"))if(matches(branch,value))count++;if(count!=1)return false;}
        for(var branch:schema.path("allOf")) if(!matches(branch,value)) return false;
        if(schema.has("if") && matches(schema.get("if"),value) && !matches(schema.get("then"),value)) return false;
        return true;
    }
    private static boolean typeMatches(String type,JsonNode value) {
        return switch(type) {
            case "object" -> value.isObject(); case "string" -> value.isString();
            case "integer" -> value.isIntegralNumber(); case "null" -> value.isNull();
            case "boolean" -> value.isBoolean(); default -> throw new AssertionError("Unsupported contract type: "+type);
        };
    }
}
