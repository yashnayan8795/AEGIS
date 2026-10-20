package io.aegis.checkout.api;

import io.aegis.checkout.application.PurchaseService;
import io.aegis.checkout.domain.Purchase;
import org.springframework.web.bind.annotation.*;

@RestController
public class PurchaseController {
    private final PurchaseService service;
    public PurchaseController(PurchaseService service) { this.service = service; }
    @GetMapping(value = "/api/v1/purchases/{purchaseId}", produces = "application/json")
    public Purchase get(@PathVariable String purchaseId) { return service.find(purchaseId); }
}
