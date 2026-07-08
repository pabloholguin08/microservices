package com.pedidos.notification_service.dto;

import com.pedidos.notification_service.domain.Notification;
import com.pedidos.notification_service.domain.NotificationType;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID orderId,
        UUID customerId,
        NotificationType type,
        String message,
        Instant sentAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getOrderId(),
                notification.getCustomerId(),
                notification.getType(),
                notification.getMessage(),
                notification.getSentAt());
    }
}
