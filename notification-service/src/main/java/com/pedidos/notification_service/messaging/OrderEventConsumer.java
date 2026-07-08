package com.pedidos.notification_service.messaging;

import com.pedidos.notification_service.domain.Notification;
import com.pedidos.notification_service.domain.NotificationType;
import com.pedidos.notification_service.messaging.event.OrderCancelledEvent;
import com.pedidos.notification_service.messaging.event.OrderConfirmedEvent;
import com.pedidos.notification_service.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final NotificationRepository notificationRepository;

    @RabbitListener(queues = RabbitMqConfig.ORDER_CONFIRMED_QUEUE)
    @Transactional
    public void handleOrderConfirmed(OrderConfirmedEvent event) {
        if (notificationRepository.existsByOrderId(event.orderId())) {
            log.info("Notification for order {} already exists, skipping (idempotent)", event.orderId());
            return;
        }

        Notification notification = new Notification();
        notification.setOrderId(event.orderId());
        notification.setCustomerId(event.customerId());
        notification.setType(NotificationType.ORDER_CONFIRMED);
        notification.setMessage("Your order " + event.orderId() + " has been confirmed.");
        notificationRepository.save(notification);

        log.info("Notification sent: order {} confirmed for customer {}", event.orderId(), event.customerId());
    }

    @RabbitListener(queues = RabbitMqConfig.ORDER_CANCELLED_QUEUE)
    @Transactional
    public void handleOrderCancelled(OrderCancelledEvent event) {
        if (notificationRepository.existsByOrderId(event.orderId())) {
            log.info("Notification for order {} already exists, skipping (idempotent)", event.orderId());
            return;
        }

        Notification notification = new Notification();
        notification.setOrderId(event.orderId());
        notification.setCustomerId(event.customerId());
        notification.setType(NotificationType.ORDER_CANCELLED);
        notification.setMessage("Your order " + event.orderId() + " was cancelled: " + event.reason());
        notificationRepository.save(notification);

        log.info("Notification sent: order {} cancelled for customer {} ({})", event.orderId(), event.customerId(), event.reason());
    }
}
