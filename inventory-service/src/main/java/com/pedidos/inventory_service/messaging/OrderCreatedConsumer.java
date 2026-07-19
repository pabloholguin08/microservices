package com.pedidos.inventory_service.messaging;

import com.pedidos.inventory_service.exception.InsufficientStockException;
import com.pedidos.inventory_service.messaging.event.OrderCreatedEvent;
import com.pedidos.inventory_service.metrics.InventoryMetrics;
import com.pedidos.inventory_service.repository.StockReservationRepository;
import com.pedidos.inventory_service.service.StockReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCreatedConsumer {

    private static final int MAX_RETRIES = 3;

    private final StockReservationRepository stockReservationRepository;
    private final StockReservationService stockReservationService;
    private final InventoryEventPublisher eventPublisher;
    private final InventoryMetrics inventoryMetrics;

    @RabbitListener(queues = RabbitMqConfig.ORDER_CREATED_QUEUE)
    public void handleOrderCreated(OrderCreatedEvent event) {
        try (var ignored = MDC.putCloseable("orderId", event.orderId().toString())) {
            if (stockReservationRepository.existsByOrderId(event.orderId())) {
                log.info("Order {} was already processed, skipping (duplicate delivery)", event.orderId());
                return;
            }

            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    stockReservationService.reserveStock(event);
                    eventPublisher.publishStockReserved(event.orderId(), event.customerId());
                    inventoryMetrics.stockReserved();
                    log.info("Reserved stock for order {} on attempt {}", event.orderId(), attempt);
                    return;
                } catch (ObjectOptimisticLockingFailureException ex) {
                    log.warn("Optimistic lock conflict reserving stock for order {} (attempt {}/{})",
                            event.orderId(), attempt, MAX_RETRIES);
                    if (attempt == MAX_RETRIES) {
                        eventPublisher.publishStockRejected(
                                event.orderId(), event.customerId(),
                                "Could not reserve stock due to concurrent updates, please retry the order");
                        inventoryMetrics.stockRejectedConcurrencyExhausted();
                        return;
                    }
                } catch (InsufficientStockException ex) {
                    log.info("Rejecting order {}: {}", event.orderId(), ex.getMessage());
                    eventPublisher.publishStockRejected(event.orderId(), event.customerId(), ex.getMessage());
                    inventoryMetrics.stockRejectedInsufficientStock();
                    return;
                }
            }
        }
    }
}
