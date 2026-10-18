package io.aegis.notification.application;

import java.util.UUID;
import java.util.regex.Pattern;

public final class RequestIds {
    private static final Pattern UUID_TEXT = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private RequestIds() {}
    public static UUID parse(String value) {
        if (value == null || !UUID_TEXT.matcher(value).matches()) { throw new InvalidRequestException(); }
        return UUID.fromString(value);
    }
}
