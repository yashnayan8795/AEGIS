package io.aegis.inventory.domain;

public record CatalogItem(String sku, long unitPriceMinor, String currency, long availableQuantity) {}
