package com.pedidos.orders_service.exception;

import com.pedidos.orders_service.domain.OrderStatus;
import java.util.UUID;

public class InvalidOrderStateException extends RuntimeException {

    public InvalidOrderStateException(UUID orderId, OrderStatus currentStatus) {
        super("Order " + orderId + " cannot be cancelled from state " + currentStatus);
    }
}
