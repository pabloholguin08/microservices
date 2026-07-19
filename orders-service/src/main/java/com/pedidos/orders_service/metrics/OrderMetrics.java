package com.pedidos.orders_service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Business-level counters, as distinct from the HTTP/JVM metrics Actuator
 * exposes automatically. These answer "how many orders got confirmed today"
 * directly in Prometheus, without deriving it from log lines.
 */
@Component
public class OrderMetrics {

    private final Counter created;
    private final Counter confirmed;
    private final Counter cancelledByCustomer;
    private final Counter cancelledByStockRejection;

    public OrderMetrics(MeterRegistry registry) {
        // Named "orders.placed", not "orders.created": Prometheus's OpenMetrics
        // naming spec treats a name ending in "_created" as the reserved
        // counter-created-timestamp marker, which silently swallows the
        // "created" segment and collapses the series into "orders_total".
        this.created = Counter.builder("orders.placed")
                .description("Number of orders placed")
                .register(registry);
        this.confirmed = Counter.builder("orders.confirmed")
                .description("Number of orders confirmed after stock reservation")
                .register(registry);
        this.cancelledByCustomer = Counter.builder("orders.cancelled")
                .description("Number of orders cancelled")
                .tag("reason", "customer")
                .register(registry);
        this.cancelledByStockRejection = Counter.builder("orders.cancelled")
                .description("Number of orders cancelled")
                .tag("reason", "stock_rejected")
                .register(registry);
    }

    public void orderCreated() {
        created.increment();
    }

    public void orderConfirmed() {
        confirmed.increment();
    }

    public void orderCancelledByCustomer() {
        cancelledByCustomer.increment();
    }

    public void orderCancelledByStockRejection() {
        cancelledByStockRejection.increment();
    }
}
