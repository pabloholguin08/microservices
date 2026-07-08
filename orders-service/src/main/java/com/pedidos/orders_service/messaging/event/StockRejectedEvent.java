package com.pedidos.orders_service.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record StockRejectedEvent(UUID orderId, UUID customerId, String reason, Instant occurredAt) {
}
