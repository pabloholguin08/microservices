package com.pedidos.inventory_service.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.pedidos.inventory_service.TestcontainersConfiguration;
import com.pedidos.inventory_service.domain.Product;
import com.pedidos.inventory_service.messaging.event.OrderCreatedEvent;
import com.pedidos.inventory_service.messaging.event.OrderItemEvent;
import com.pedidos.inventory_service.repository.ProductRepository;
import com.pedidos.inventory_service.repository.StockReservationRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Verifies that redelivering the same order.created event (at-least-once delivery) does not
 * deduct stock twice, satisfying the idempotency requirement for RabbitMQ consumers.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OrderCreatedConsumerIdempotencyTest {

    @Autowired
    private OrderCreatedConsumer orderCreatedConsumer;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockReservationRepository stockReservationRepository;

    @Test
    void duplicateDeliveryDoesNotDoubleDeductStock() {
        Product product = new Product();
        product.setName("Duplicate Test Item");
        product.setSku("SKU-DUP-" + UUID.randomUUID());
        product.setPrice(new BigDecimal("5.00"));
        product.setStockQuantity(10);
        product = productRepository.saveAndFlush(product);
        UUID productId = product.getId();

        UUID orderId = UUID.randomUUID();
        OrderCreatedEvent event = new OrderCreatedEvent(
                orderId, UUID.randomUUID(), List.of(new OrderItemEvent(productId, 3)), Instant.now());

        orderCreatedConsumer.handleOrderCreated(event);
        orderCreatedConsumer.handleOrderCreated(event);

        Product afterBothDeliveries = productRepository.findById(productId).orElseThrow();
        assertThat(afterBothDeliveries.getStockQuantity()).isEqualTo(7);
        assertThat(stockReservationRepository.countByOrderId(orderId)).isEqualTo(1);
    }
}
