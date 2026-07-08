package com.pedidos.orders_service.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.pedidos.orders_service.TestcontainersConfiguration;
import com.pedidos.orders_service.domain.Order;
import com.pedidos.orders_service.domain.OrderItem;
import com.pedidos.orders_service.domain.OrderStatus;
import com.pedidos.orders_service.messaging.event.StockRejectedEvent;
import com.pedidos.orders_service.messaging.event.StockReservedEvent;
import com.pedidos.orders_service.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Verifies that redelivered stock.reserved/stock.rejected events are ignored once an order has
 * already left the CREATED state, so the saga's terminal state is never flipped by a duplicate.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StockEventConsumerIdempotencyTest {

    @Autowired
    private StockEventConsumer stockEventConsumer;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void duplicateStockReservedEventDoesNotReprocessAlreadyConfirmedOrder() {
        Order order = newOrder();
        order = orderRepository.save(order);
        UUID orderId = order.getId();

        StockReservedEvent event = new StockReservedEvent(orderId, order.getCustomerId(), Instant.now());

        stockEventConsumer.handleStockReserved(event);
        stockEventConsumer.handleStockReserved(event);

        Order finalOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(finalOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void stockRejectedIsIgnoredWhenOrderAlreadyConfirmed() {
        Order order = newOrder();
        order = orderRepository.save(order);
        UUID orderId = order.getId();

        stockEventConsumer.handleStockReserved(new StockReservedEvent(orderId, order.getCustomerId(), Instant.now()));
        stockEventConsumer.handleStockRejected(
                new StockRejectedEvent(orderId, order.getCustomerId(), "late rejection", Instant.now()));

        Order finalOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(finalOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    private Order newOrder() {
        Order order = new Order();
        order.setCustomerId(UUID.randomUUID());
        OrderItem item = new OrderItem();
        item.setProductId(UUID.randomUUID());
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("10.00"));
        order.addItem(item);
        order.setTotalAmount(new BigDecimal("10.00"));
        return order;
    }
}
