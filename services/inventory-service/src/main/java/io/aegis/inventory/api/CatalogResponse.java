package io.aegis.inventory.api;

import java.time.Instant;

public record CatalogResponse(String sku, long unitPriceMinor, String currency,
                              long availableQuantity, Instant observedAt) {}
