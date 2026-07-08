package com.pedidos.inventory_service.messaging.event;

import java.util.UUID;

public record OrderItemEvent(UUID productId, int quantity) {
}
