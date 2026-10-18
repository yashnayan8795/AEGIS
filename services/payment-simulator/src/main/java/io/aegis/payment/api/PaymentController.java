package io.aegis.payment.api;

import io.aegis.payment.application.PaymentService;
import io.aegis.payment.domain.Payment;
import org.springframework.web.bind.annotation.*;

@RestController
public class PaymentController {
    private final PaymentService service;
    public PaymentController(PaymentService service) { this.service = service; }
    @GetMapping(value = "/api/v1/payments/{paymentId}", produces = "application/json")
    public Payment get(@PathVariable String paymentId) { return service.find(paymentId); }
}
