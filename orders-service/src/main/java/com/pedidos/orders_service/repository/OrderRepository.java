package com.pedidos.orders_service.repository;

import com.pedidos.orders_service.domain.Order;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @EntityGraph(attributePaths = "items")
    @Override
    Optional<Order> findById(UUID id);

    @EntityGraph(attributePaths = "items")
    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);
}
