package io.aegis.notification.api;

import io.aegis.notification.application.NotificationService;
import io.aegis.notification.domain.NotificationReceipt;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service) { this.service = service; }
    @PostMapping(value = "/api/v1/notifications", consumes = "application/json", produces = "application/json")
    public ResponseEntity<NotificationReceipt> store(@RequestBody JsonNode json) {
        var request = NotificationRequest.parse(json);
        var result = service.store(request.notificationId(), request.purchaseId(), request.kind());
        return result.created()
                ? ResponseEntity.created(URI.create("/api/v1/notifications/" + result.receipt().notificationId())).body(result.receipt())
                : ResponseEntity.ok(result.receipt());
    }
    @GetMapping(value = "/api/v1/notifications/{notificationId}", produces = "application/json")
    public NotificationReceipt get(@PathVariable String notificationId) { return service.find(notificationId); }
}
