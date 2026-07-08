package com.pedidos.inventory_service.dto;

import com.pedidos.inventory_service.domain.Product;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(UUID id, String name, String sku, BigDecimal price, Integer stockQuantity) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(), product.getName(), product.getSku(), product.getPrice(), product.getStockQuantity());
    }
}
