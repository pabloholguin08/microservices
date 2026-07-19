package com.pedidos.orders_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pedidos.orders_service.domain.Order;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventPublisher eventPublisher;

    @Mock
    private OrderMetrics orderMetrics;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createOrderCalculatesTotalAndPublishesOrderCreated() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        CreateOrderRequest request = new CreateOrderRequest(
                customerId, List.of(new CreateOrderItemRequest(productId, 3, new BigDecimal("10.00"))));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });

        Order result = orderService.createOrder(request);

        assertThat(result.getTotalAmount()).isEqualByComparingTo("30.00");
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(result.getItems()).hasSize(1);
        verify(eventPublisher).publishOrderCreated(result);
    }

    @Test
    void cancelOrderFromCreatedTransitionsToCancelledAndPublishes() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setCustomerId(UUID.randomUUID());
        order.setStatus(OrderStatus.CREATED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        Order result = orderService.cancelOrder(orderId);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(eventPublisher).publishOrderCancelled(order, "CANCELLED_BY_CUSTOMER");
    }

    @Test
    void cancelOrderFromConfirmedThrowsInvalidOrderStateException() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(orderId)).isInstanceOf(InvalidOrderStateException.class);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void getOrderThrowsWhenNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(orderId)).isInstanceOf(OrderNotFoundException.class);
    }
}
