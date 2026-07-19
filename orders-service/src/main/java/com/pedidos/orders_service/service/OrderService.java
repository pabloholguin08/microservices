package com.pedidos.orders_service.service;

import com.pedidos.orders_service.domain.Order;
import com.pedidos.orders_service.domain.OrderItem;
import com.pedidos.orders_service.domain.OrderStatus;
import com.pedidos.orders_service.dto.CreateOrderItemRequest;
import com.pedidos.orders_service.dto.CreateOrderRequest;
import com.pedidos.orders_service.exception.InvalidOrderStateException;
import com.pedidos.orders_service.exception.OrderNotFoundException;
import com.pedidos.orders_service.messaging.OrderEventPublisher;
import com.pedidos.orders_service.metrics.OrderMetrics;
import com.pedidos.orders_service.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;
    private final OrderMetrics orderMetrics;

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        Order order = new Order();
        order.setCustomerId(request.customerId());

        for (CreateOrderItemRequest itemRequest : request.items()) {
            OrderItem item = new OrderItem();
            item.setProductId(itemRequest.productId());
            item.setQuantity(itemRequest.quantity());
            item.setUnitPrice(itemRequest.unitPrice());
            order.addItem(item);
        }

        order.setTotalAmount(calculateTotal(order.getItems()));

        Order saved = orderRepository.save(order);

        try (var ignored = MDC.putCloseable("orderId", saved.getId().toString())) {
            log.info("Order {} created for customer {} with {} item(s)", saved.getId(), saved.getCustomerId(), saved.getItems().size());
            orderMetrics.orderCreated();
            eventPublisher.publishOrderCreated(saved);
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public Order getOrder(UUID orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Transactional(readOnly = true)
    public Page<Order> getOrdersByCustomer(UUID customerId, Pageable pageable) {
        return orderRepository.findByCustomerId(customerId, pageable);
    }

    @Transactional
    public Order cancelOrder(UUID orderId) {
        try (var ignored = MDC.putCloseable("orderId", orderId.toString())) {
            Order order = getOrder(orderId);
            if (order.getStatus() != OrderStatus.CREATED) {
                throw new InvalidOrderStateException(orderId, order.getStatus());
            }

            order.setStatus(OrderStatus.CANCELLED);
            log.info("Order {} cancelled by customer request", orderId);
            orderMetrics.orderCancelledByCustomer();

            eventPublisher.publishOrderCancelled(order, "CANCELLED_BY_CUSTOMER");
            return order;
        }
    }

    private BigDecimal calculateTotal(List<OrderItem> items) {
        return items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
