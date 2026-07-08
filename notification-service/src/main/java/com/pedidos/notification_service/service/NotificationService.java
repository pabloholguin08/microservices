package com.pedidos.notification_service.service;

import com.pedidos.notification_service.domain.Notification;
import com.pedidos.notification_service.repository.NotificationRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public Page<Notification> getByCustomer(UUID customerId, Pageable pageable) {
        return notificationRepository.findByCustomerId(customerId, pageable);
    }
}
