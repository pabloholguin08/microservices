package com.pedidos.notification_service.repository;

import com.pedidos.notification_service.domain.Notification;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    boolean existsByOrderId(UUID orderId);

    Page<Notification> findByCustomerId(UUID customerId, Pageable pageable);
}
