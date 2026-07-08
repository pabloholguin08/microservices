package com.pedidos.inventory_service.messaging;

import com.pedidos.inventory_service.messaging.event.StockRejectedEvent;
import com.pedidos.inventory_service.messaging.event.StockReservedEvent;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishStockReserved(UUID orderId, UUID customerId) {
        send(RabbitMqConfig.ROUTING_KEY_STOCK_RESERVED, orderId, new StockReservedEvent(orderId, customerId, Instant.now()));
    }

    public void publishStockRejected(UUID orderId, UUID customerId, String reason) {
        send(RabbitMqConfig.ROUTING_KEY_STOCK_REJECTED, orderId, new StockRejectedEvent(orderId, customerId, reason, Instant.now()));
    }

    private void send(String routingKey, UUID correlationId, Object event) {
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.ORDERS_EXCHANGE,
                routingKey,
                event,
                message -> {
                    message.getMessageProperties().setCorrelationId(correlationId.toString());
                    return message;
                });
        log.info("Published {} routingKey={} correlationId={}", event.getClass().getSimpleName(), routingKey, correlationId);
    }
}
