package io.aegis.notification.api;

import io.aegis.notification.application.InvalidRequestException;
import tools.jackson.databind.JsonNode;

/** Strict wire parsing: no numeric-to-string coercion, nulls or unknown properties. */
public record NotificationRequest(String notificationId, String purchaseId, String kind) {
    public static NotificationRequest parse(JsonNode json) {
        if (json == null || !json.isObject() || json.size() != 3) { throw new InvalidRequestException(); }
        return new NotificationRequest(text(json, "notificationId"), text(json, "purchaseId"), text(json, "kind"));
    }
    private static String text(JsonNode json, String field) {
        var value = json.get(field);
        if (value == null || !value.isString()) { throw new InvalidRequestException(); }
        return value.asText();
    }
}
