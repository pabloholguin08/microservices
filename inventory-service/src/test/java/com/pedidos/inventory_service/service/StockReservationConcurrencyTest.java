package com.pedidos.inventory_service.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pedidos.inventory_service.TestcontainersConfiguration;
import com.pedidos.inventory_service.domain.Product;
import com.pedidos.inventory_service.exception.InsufficientStockException;
import com.pedidos.inventory_service.messaging.event.OrderCreatedEvent;
import com.pedidos.inventory_service.messaging.event.OrderItemEvent;
import com.pedidos.inventory_service.repository.ProductRepository;
import com.pedidos.inventory_service.repository.StockReservationRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Verifies that the @Version optimistic-locking column on Product prevents two concurrent
 * orders from both reserving the last unit of stock.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StockReservationConcurrencyTest {

    @Autowired
    private StockReservationService stockReservationService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockReservationRepository stockReservationRepository;

    @Test
    void onlyOneOfTwoConcurrentOrdersReservesTheLastUnit() throws Exception {
        Product product = new Product();
        product.setName("Limited Item");
        product.setSku("SKU-LIMITED-" + UUID.randomUUID());
        product.setPrice(new BigDecimal("9.99"));
        product.setStockQuantity(1);
        product = productRepository.saveAndFlush(product);
        UUID productId = product.getId();

        OrderCreatedEvent eventA = new OrderCreatedEvent(
                UUID.randomUUID(), UUID.randomUUID(), List.of(new OrderItemEvent(productId, 1)), Instant.now());
        OrderCreatedEvent eventB = new OrderCreatedEvent(
                UUID.randomUUID(), UUID.randomUUID(), List.of(new OrderItemEvent(productId, 1)), Instant.now());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<Boolean> attemptA = attempt(eventA, readyLatch, startLatch);
        Callable<Boolean> attemptB = attempt(eventB, readyLatch, startLatch);

        Future<Boolean> resultA = executor.submit(attemptA);
        Future<Boolean> resultB = executor.submit(attemptB);

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();

        boolean succeededA = resultA.get(10, TimeUnit.SECONDS);
        boolean succeededB = resultB.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(succeededA ^ succeededB).as("exactly one of the two concurrent reservations should succeed").isTrue();

        Product finalProduct = productRepository.findById(productId).orElseThrow();
        assertThat(finalProduct.getStockQuantity()).isZero();
        assertThat(stockReservationRepository.countByProductId(productId)).isEqualTo(1);
    }

    private Callable<Boolean> attempt(OrderCreatedEvent event, CountDownLatch readyLatch, CountDownLatch startLatch) {
        return () -> {
            readyLatch.countDown();
            startLatch.await();
            try {
                stockReservationService.reserveStock(event);
                return true;
            } catch (ObjectOptimisticLockingFailureException | InsufficientStockException ex) {
                return false;
            }
        };
    }
}
