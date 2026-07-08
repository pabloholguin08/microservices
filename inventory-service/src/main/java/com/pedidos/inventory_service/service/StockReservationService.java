package com.pedidos.inventory_service.service;

import com.pedidos.inventory_service.domain.Product;
import com.pedidos.inventory_service.domain.StockReservation;
import com.pedidos.inventory_service.exception.InsufficientStockException;
import com.pedidos.inventory_service.messaging.event.OrderCreatedEvent;
import com.pedidos.inventory_service.messaging.event.OrderItemEvent;
import com.pedidos.inventory_service.repository.ProductRepository;
import com.pedidos.inventory_service.repository.StockReservationRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs each stock-reservation attempt in its own fresh transaction (REQUIRES_NEW) so that a
 * failed optimistic-lock flush on one attempt cannot poison the persistence context of a retry.
 */
@Service
@RequiredArgsConstructor
public class StockReservationService {

    private final ProductRepository productRepository;
    private final StockReservationRepository stockReservationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserveStock(OrderCreatedEvent event) {
        List<StockReservation> reservations = new ArrayList<>();

        for (OrderItemEvent item : event.items()) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new InsufficientStockException("Product not found: " + item.productId()));

            if (product.getStockQuantity() < item.quantity()) {
                throw new InsufficientStockException(
                        "Insufficient stock for product " + product.getSku() + " (sku=" + product.getSku() + ")");
            }

            product.setStockQuantity(product.getStockQuantity() - item.quantity());
            productRepository.saveAndFlush(product);

            StockReservation reservation = new StockReservation();
            reservation.setOrderId(event.orderId());
            reservation.setProductId(item.productId());
            reservation.setQuantity(item.quantity());
            reservations.add(reservation);
        }

        stockReservationRepository.saveAll(reservations);
    }
}
