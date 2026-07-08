package com.pedidos.inventory_service.controller;

import com.pedidos.inventory_service.dto.CreateProductRequest;
import com.pedidos.inventory_service.dto.PageResponse;
import com.pedidos.inventory_service.dto.ProductResponse;
import com.pedidos.inventory_service.dto.UpdateStockRequest;
import com.pedidos.inventory_service.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Validated
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public PageResponse<ProductResponse> getProducts(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name"));
        return PageResponse.from(productService.getAllProducts(pageable).map(ProductResponse::from));
    }

    @GetMapping("/{id}")
    public ProductResponse getProduct(@PathVariable UUID id) {
        return ProductResponse.from(productService.getProduct(id));
    }

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request, UriComponentsBuilder uriBuilder) {
        ProductResponse product = ProductResponse.from(productService.createProduct(request));
        URI location = uriBuilder.path("/api/v1/products/{id}").buildAndExpand(product.id()).toUri();
        return ResponseEntity.created(location).body(product);
    }

    @PutMapping("/{id}/stock")
    public ProductResponse updateStock(@PathVariable UUID id, @Valid @RequestBody UpdateStockRequest request) {
        return ProductResponse.from(productService.updateStock(id, request));
    }
}
