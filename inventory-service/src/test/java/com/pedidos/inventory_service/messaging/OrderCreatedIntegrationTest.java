package com.pedidos.inventory_service.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.pedidos.inventory_service.TestcontainersConfiguration;
import com.pedidos.inventory_service.domain.Product;
import com.pedidos.inventory_service.messaging.event.OrderCreatedEvent;
import com.pedidos.inventory_service.messaging.event.OrderItemEvent;
import com.pedidos.inventory_service.messaging.event.StockRejectedEvent;
import com.pedidos.inventory_service.messaging.event.StockReservedEvent;
import com.pedidos.inventory_service.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

/**
 * End-to-end integration test using real Postgres and RabbitMQ containers: publishes an actual
 * order.created message onto the exchange and asserts inventory-service's consumer produces the
 * correct downstream event.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OrderCreatedIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void publishingOrderCreatedResultsInStockReservedEvent() {
        Product product = new Product();
        product.setName("Integration Test Item");
        product.setSku("SKU-INT-RESERVED-" + UUID.randomUUID());
        product.setPrice(new BigDecimal("15.00"));
        product.setStockQuantity(5);
        product = productRepository.saveAndFlush(product);

        String testQueueName = bindTestQueue(RabbitMqConfig.ROUTING_KEY_STOCK_RESERVED);

        UUID orderId = UUID.randomUUID();
        OrderCreatedEvent event = new OrderCreatedEvent(
                orderId, UUID.randomUUID(), List.of(new OrderItemEvent(product.getId(), 2)), Instant.now());

        rabbitTemplate.convertAndSend(RabbitMqConfig.ORDERS_EXCHANGE, RabbitMqConfig.ROUTING_KEY_ORDER_CREATED, event);

        Message received = rabbitTemplate.receive(testQueueName, 5000);
        assertThat(received).as("expected a message on %s within the timeout", testQueueName).isNotNull();

        StockReservedEvent stockReservedEvent = jsonMapper.readValue(received.getBody(), StockReservedEvent.class);
        assertThat(stockReservedEvent.orderId()).isEqualTo(orderId);
    }

    @Test
    void publishingOrderCreatedWithInsufficientStockResultsInStockRejectedEvent() {
        Product product = new Product();
        product.setName("Integration Test Scarce Item");
        product.setSku("SKU-INT-REJECTED-" + UUID.randomUUID());
        product.setPrice(new BigDecimal("15.00"));
        product.setStockQuantity(1);
        product = productRepository.saveAndFlush(product);

        String testQueueName = bindTestQueue(RabbitMqConfig.ROUTING_KEY_STOCK_REJECTED);

        UUID orderId = UUID.randomUUID();
        OrderCreatedEvent event = new OrderCreatedEvent(
                orderId, UUID.randomUUID(), List.of(new OrderItemEvent(product.getId(), 100)), Instant.now());

        rabbitTemplate.convertAndSend(RabbitMqConfig.ORDERS_EXCHANGE, RabbitMqConfig.ROUTING_KEY_ORDER_CREATED, event);

        Message received = rabbitTemplate.receive(testQueueName, 5000);
        assertThat(received).as("expected a message on %s within the timeout", testQueueName).isNotNull();

        StockRejectedEvent stockRejectedEvent = jsonMapper.readValue(received.getBody(), StockRejectedEvent.class);
        assertThat(stockRejectedEvent.orderId()).isEqualTo(orderId);
        assertThat(stockRejectedEvent.reason()).isNotBlank();
    }

    private String bindTestQueue(String routingKey) {
        String queueName = "test." + routingKey + "." + UUID.randomUUID();
        org.springframework.amqp.core.Queue testQueue = QueueBuilder.durable(queueName).autoDelete().build();
        amqpAdmin.declareQueue(testQueue);
        amqpAdmin.declareBinding(BindingBuilder.bind(testQueue)
                .to(new TopicExchange(RabbitMqConfig.ORDERS_EXCHANGE))
                .with(routingKey));
        return queueName;
    }
}
