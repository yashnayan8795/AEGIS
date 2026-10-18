package io.aegis.payment.application;

import io.aegis.payment.domain.Payment;
import io.aegis.payment.infrastructure.persistence.PaymentRepository;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {
    private final PaymentRepository repository;
    public PaymentService(PaymentRepository repository) { this.repository = repository; }
    public Payment find(String id) {
        return repository.findById(RequestIds.parse(id)).orElseThrow(PaymentNotFoundException::new);
    }
}
