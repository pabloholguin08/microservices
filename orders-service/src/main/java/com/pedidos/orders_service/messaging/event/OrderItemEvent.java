package com.pedidos.orders_service.messaging.event;

import java.util.UUID;

public record OrderItemEvent(UUID productId, int quantity) {
}
