package com.pedidos.inventory_service.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record StockRejectedEvent(UUID orderId, UUID customerId, String reason, Instant occurredAt) {
}
