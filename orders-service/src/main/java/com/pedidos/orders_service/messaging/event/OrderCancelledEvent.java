package com.pedidos.orders_service.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record OrderCancelledEvent(
        UUID orderId,
        UUID customerId,
        String reason,
        Instant occurredAt) {
}
