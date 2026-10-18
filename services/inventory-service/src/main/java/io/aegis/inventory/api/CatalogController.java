package io.aegis.inventory.api;

import io.aegis.inventory.application.CatalogService;
import java.time.Clock;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CatalogController {
    private final CatalogService service;
    private final Clock clock;
    public CatalogController(CatalogService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping(value = "/api/v1/items/{sku}", produces = "application/json")
    public CatalogResponse get(@PathVariable String sku) {
        // Timestamp the read attempt, not a promise that this stock remains available.
        Instant observedAt = clock.instant();
        var item = service.find(sku);
        return new CatalogResponse(item.sku(), item.unitPriceMinor(), item.currency(),
                item.availableQuantity(), observedAt);
    }
}
