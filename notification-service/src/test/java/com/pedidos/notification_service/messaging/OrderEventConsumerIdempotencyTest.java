package com.pedidos.notification_service.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.pedidos.notification_service.TestcontainersConfiguration;
import com.pedidos.notification_service.domain.Notification;
import com.pedidos.notification_service.domain.NotificationType;
import com.pedidos.notification_service.messaging.event.OrderCancelledEvent;
import com.pedidos.notification_service.messaging.event.OrderConfirmedEvent;
import com.pedidos.notification_service.repository.NotificationRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Verifies that redelivering the same order.confirmed/order.cancelled event does not create a
 * duplicate Notification record.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OrderEventConsumerIdempotencyTest {

    @Autowired
    private OrderEventConsumer orderEventConsumer;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void duplicateOrderConfirmedEventCreatesOnlyOneNotification() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderConfirmedEvent event = new OrderConfirmedEvent(orderId, customerId, Instant.now());

        orderEventConsumer.handleOrderConfirmed(event);
        orderEventConsumer.handleOrderConfirmed(event);

        Page<Notification> notifications = notificationRepository.findByCustomerId(customerId, Pageable.unpaged());
        assertThat(notifications.getTotalElements()).isEqualTo(1);
        assertThat(notifications.getContent().get(0).getType()).isEqualTo(NotificationType.ORDER_CONFIRMED);
    }

    @Test
    void duplicateOrderCancelledEventCreatesOnlyOneNotification() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderCancelledEvent event = new OrderCancelledEvent(orderId, customerId, "out of stock", Instant.now());

        orderEventConsumer.handleOrderCancelled(event);
        orderEventConsumer.handleOrderCancelled(event);

        Page<Notification> notifications = notificationRepository.findByCustomerId(customerId, Pageable.unpaged());
        assertThat(notifications.getTotalElements()).isEqualTo(1);
        assertThat(notifications.getContent().get(0).getType()).isEqualTo(NotificationType.ORDER_CANCELLED);
    }
}
