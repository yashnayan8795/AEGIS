package io.aegis.notification.application;

import io.aegis.notification.domain.NotificationReceipt;
import io.aegis.notification.infrastructure.persistence.NotificationRepository;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final NotificationRepository repository;
    public NotificationService(NotificationRepository repository) { this.repository = repository; }
    public record Stored(NotificationReceipt receipt, boolean created) {}

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Stored store(String id, String purchase, String kind) {
        var notificationId = RequestIds.parse(id);
        var purchaseId = RequestIds.parse(purchase);
        if (!"PURCHASE_CONFIRMED".equals(kind)) { throw new InvalidRequestException(); }
        var inserted = repository.insertIfAbsent(notificationId, purchaseId, kind);
        if (inserted.isPresent()) { return new Stored(inserted.get(), true); }
        // A separate statement gets a fresh READ COMMITTED snapshot after a competing insert.
        var existing = repository.findById(notificationId).orElseThrow(() ->
                new DataRetrievalFailureException("Conflicting receipt no longer available"));
        if (!existing.purchaseId().equals(purchaseId) || !existing.kind().equals(kind)) {
            throw new NotificationConflictException();
        }
        return new Stored(existing, false);
        // Spring's proxy commits before control returns to the HTTP controller.
    }
    public NotificationReceipt find(String id) {
        return repository.findById(RequestIds.parse(id)).orElseThrow(NotificationNotFoundException::new);
    }
}
