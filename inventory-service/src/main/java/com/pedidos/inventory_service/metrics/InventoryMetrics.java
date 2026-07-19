package com.pedidos.inventory_service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Business-level counters, as distinct from the HTTP/JVM metrics Actuator
 * exposes automatically.
 */
@Component
public class InventoryMetrics {

    private final Counter stockReserved;
    private final Counter stockRejectedInsufficient;
    private final Counter stockRejectedConcurrencyExhausted;

    public InventoryMetrics(MeterRegistry registry) {
        this.stockReserved = Counter.builder("stock.reservations")
                .description("Number of successful stock reservations")
                .tag("outcome", "reserved")
                .register(registry);
        this.stockRejectedInsufficient = Counter.builder("stock.rejections")
                .description("Number of orders rejected for insufficient stock")
                .tag("reason", "insufficient_stock")
                .register(registry);
        this.stockRejectedConcurrencyExhausted = Counter.builder("stock.rejections")
                .description("Number of orders rejected after exhausting optimistic-lock retries")
                .tag("reason", "concurrency_conflict")
                .register(registry);
    }

    public void stockReserved() {
        stockReserved.increment();
    }

    public void stockRejectedInsufficientStock() {
        stockRejectedInsufficient.increment();
    }

    public void stockRejectedConcurrencyExhausted() {
        stockRejectedConcurrencyExhausted.increment();
    }
}
