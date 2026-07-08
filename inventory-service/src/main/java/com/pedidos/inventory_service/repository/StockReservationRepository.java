package com.pedidos.inventory_service.repository;

import com.pedidos.inventory_service.domain.StockReservation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {

    boolean existsByOrderId(UUID orderId);

    long countByOrderId(UUID orderId);

    long countByProductId(UUID productId);
}
