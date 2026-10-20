package io.aegis.checkout.application;

import io.aegis.checkout.domain.Purchase;
import io.aegis.checkout.infrastructure.persistence.PurchaseRepository;
import org.springframework.stereotype.Service;

@Service
public class PurchaseService {
    private final PurchaseRepository repository;
    public PurchaseService(PurchaseRepository repository) { this.repository = repository; }
    public Purchase find(String id) {
        return repository.findById(RequestIds.parse(id)).orElseThrow(PurchaseNotFoundException::new);
    }
}
