package com.pedidos.orders_service.messaging;

import com.pedidos.orders_service.domain.Order;
import com.pedidos.orders_service.messaging.event.OrderCancelledEvent;
import com.pedidos.orders_service.messaging.event.OrderConfirmedEvent;
import com.pedidos.orders_service.messaging.event.OrderCreatedEvent;
import com.pedidos.orders_service.messaging.event.OrderItemEvent;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishOrderCreated(Order order) {
        OrderCreatedEvent event = new OrderCreatedEvent(
                order.getId(),
                order.getCustomerId(),
                order.getItems().stream()
                        .map(item -> new OrderItemEvent(item.getProductId(), item.getQuantity()))
                        .toList(),
                Instant.now());

        send(RabbitMqConfig.ROUTING_KEY_ORDER_CREATED, order.getId(), event);
    }

    public void publishOrderConfirmed(Order order) {
        OrderConfirmedEvent event = new OrderConfirmedEvent(order.getId(), order.getCustomerId(), Instant.now());

        send(RabbitMqConfig.ROUTING_KEY_ORDER_CONFIRMED, order.getId(), event);
    }

    public void publishOrderCancelled(Order order, String reason) {
        OrderCancelledEvent event = new OrderCancelledEvent(
                order.getId(), order.getCustomerId(), reason, Instant.now());

        send(RabbitMqConfig.ROUTING_KEY_ORDER_CANCELLED, order.getId(), event);
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
