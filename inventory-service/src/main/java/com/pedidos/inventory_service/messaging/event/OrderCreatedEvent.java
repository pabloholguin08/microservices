package com.pedidos.inventory_service.messaging.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID orderId,
        UUID customerId,
        List<OrderItemEvent> items,
        Instant occurredAt) {
}
