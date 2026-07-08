package com.pedidos.orders_service.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record OrderConfirmedEvent(UUID orderId, UUID customerId, Instant occurredAt) {
}
