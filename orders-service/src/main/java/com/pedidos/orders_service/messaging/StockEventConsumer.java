package com.pedidos.orders_service.messaging;

import com.pedidos.orders_service.domain.Order;
import com.pedidos.orders_service.domain.OrderStatus;
import com.pedidos.orders_service.messaging.event.StockRejectedEvent;
import com.pedidos.orders_service.messaging.event.StockReservedEvent;
import com.pedidos.orders_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockEventConsumer {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;

    @RabbitListener(queues = RabbitMqConfig.STOCK_RESERVED_QUEUE)
    @Transactional
    public void handleStockReserved(StockReservedEvent event) {
        Order order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null) {
            log.warn("Received stock.reserved for unknown order {}", event.orderId());
            return;
        }
        if (order.getStatus() != OrderStatus.CREATED) {
            log.info("Order {} already in status {}, ignoring stock.reserved (idempotent)", event.orderId(), order.getStatus());
            return;
        }

        order.setStatus(OrderStatus.CONFIRMED);
        eventPublisher.publishOrderConfirmed(order);
        log.info("Order {} confirmed", event.orderId());
    }

    @RabbitListener(queues = RabbitMqConfig.STOCK_REJECTED_QUEUE)
    @Transactional
    public void handleStockRejected(StockRejectedEvent event) {
        Order order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null) {
            log.warn("Received stock.rejected for unknown order {}", event.orderId());
            return;
        }
        if (order.getStatus() != OrderStatus.CREATED) {
            log.info("Order {} already in status {}, ignoring stock.rejected (idempotent)", event.orderId(), order.getStatus());
            return;
        }

        order.setStatus(OrderStatus.CANCELLED);
        eventPublisher.publishOrderCancelled(order, event.reason());
        log.info("Order {} cancelled: {}", event.orderId(), event.reason());
    }
}
