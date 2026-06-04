package com.shopwave.service;

import com.shopwave.client.InventoryClient;
import com.shopwave.client.InventoryView;
import com.shopwave.domain.Category;
import com.shopwave.domain.Product;
import com.shopwave.dto.ProductDto;
import com.shopwave.exception.NotFoundException;
import com.shopwave.repository.CategoryRepository;
import com.shopwave.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository  productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryClient    inventoryClient;
    private final AuditService       auditService;

    @Transactional(readOnly = true)
    public List<ProductDto> listAll() {
        return productRepository.findAllActive()
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ProductDto getById(Long id) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found: " + id));
        return toDto(p);
    }

    @Transactional(readOnly = true)
    public ProductDto getBySku(String sku) {
        Product p = productRepository.findBySku(sku)
                .orElseThrow(() -> new NotFoundException("Product not found: " + sku));
        return toDto(p);
    }

    @Transactional(readOnly = true)
    public List<ProductDto> listByCategory(Long categoryId) {
        return productRepository.findByCategoryIdAndActiveTrue(categoryId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public ProductDto create(String sku, String name, String description,
                             BigDecimal price, Long categoryId, int initialStock) {
        if (productRepository.findBySku(sku).isPresent()) {
            throw new IllegalArgumentException("SKU already exists: " + sku);
        }

        Category category = categoryId != null
                ? categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new NotFoundException("Category not found: " + categoryId))
                : null;

        Product product = Product.builder()
                .sku(sku).name(name).description(description)
                .price(price).category(category).active(true)
                .build();
        productRepository.save(product);

        inventoryClient.initStock(product.getId(), initialStock);

        auditService.log("PRODUCT_CREATED", "Product", product.getId(),
                "sku=" + sku + " stock=" + initialStock);

        log.info("Product created sku={} id={}", sku, product.getId());
        return toDto(product);
    }

    @Transactional
    public ProductDto updatePrice(Long id, BigDecimal newPrice) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found: " + id));

        BigDecimal oldPrice = product.getPrice();
        product.setPrice(newPrice);
        productRepository.save(product);

        auditService.log("PRODUCT_PRICE_UPDATED", "Product", id,
                "old=" + oldPrice + " new=" + newPrice);
        return toDto(product);
    }

    @Transactional
    public void deactivate(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found: " + id));
        product.setActive(false);
        productRepository.save(product);
        auditService.log("PRODUCT_DEACTIVATED", "Product", id, null);
    }

    ProductDto toDto(Product p) {
        Integer available = null;
        try {
            InventoryView inv = inventoryClient.getByProductId(p.getId());
            available = inv.getAvailable();
        } catch (NotFoundException ignored) {
            // ürün var, stok kaydı henüz yok
        }
        return ProductDto.builder()
                .id(p.getId())
                .sku(p.getSku())
                .name(p.getName())
                .description(p.getDescription())
                .price(p.getPrice())
                .categoryName(p.getCategory() != null ? p.getCategory().getName() : null)
                .active(p.isActive())
                .availableStock(available)
                .createdAt(p.getCreatedAt())
                .build();
    }
}
