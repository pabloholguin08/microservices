package com.pedidos.inventory_service.service;

import com.pedidos.inventory_service.domain.Product;
import com.pedidos.inventory_service.dto.CreateProductRequest;
import com.pedidos.inventory_service.dto.UpdateStockRequest;
import com.pedidos.inventory_service.exception.DuplicateSkuException;
import com.pedidos.inventory_service.exception.ProductNotFoundException;
import com.pedidos.inventory_service.repository.ProductRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Page<Product> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Product getProduct(UUID id) {
        return productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional
    public Product createProduct(CreateProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new DuplicateSkuException(request.sku());
        }

        Product product = new Product();
        product.setName(request.name());
        product.setSku(request.sku());
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
        return productRepository.save(product);
    }

    @Transactional
    public Product updateStock(UUID id, UpdateStockRequest request) {
        Product product = getProduct(id);
        product.setStockQuantity(request.stockQuantity());
        return product;
    }
}
