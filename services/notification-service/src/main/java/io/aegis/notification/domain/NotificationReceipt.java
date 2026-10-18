package io.aegis.notification.domain;

import java.time.Instant;
import java.util.UUID;

/** STORED is a synthetic local receipt, not email delivery or purchase authority. */
public record NotificationReceipt(UUID notificationId, UUID purchaseId, String kind,
                                  String state, Instant createdAt) {}
