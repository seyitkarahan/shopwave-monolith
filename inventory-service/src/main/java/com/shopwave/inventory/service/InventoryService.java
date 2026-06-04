package com.shopwave.inventory.service;

import com.shopwave.inventory.config.ChaosProperties;
import com.shopwave.inventory.domain.Inventory;
import com.shopwave.inventory.dto.InventoryView;
import com.shopwave.inventory.exception.ChaosFailureException;
import com.shopwave.inventory.exception.InsufficientStockException;
import com.shopwave.inventory.exception.NotFoundException;
import com.shopwave.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final AuditService auditService;
    private final ChaosProperties chaos;

    private void applyChaosOnReserve() {
        if (!chaos.isEnabled()) {
            return;
        }
        if (chaos.getDelayMs() > 0) {
            try {
                Thread.sleep(chaos.getDelayMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (chaos.getFailRate() > 0 && ThreadLocalRandom.current().nextDouble() < chaos.getFailRate()) {
            log.warn("LAB-6 chaos: returning simulated 503 on reserve");
            throw new ChaosFailureException();
        }
    }

    @Transactional(readOnly = true)
    public InventoryView getByProductId(Long productId) {
        return InventoryView.from(find(productId));
    }

    @Transactional(readOnly = true)
    public List<InventoryView> getLowStock(int threshold) {
        return inventoryRepository.findLowStock(threshold).stream()
                .map(InventoryView::from)
                .toList();
    }

    @Transactional
    public InventoryView initStock(Long productId, int quantity) {
        if (inventoryRepository.existsByProductId(productId)) {
            throw new IllegalArgumentException("Inventory already exists for product: " + productId);
        }
        Inventory inv = Inventory.builder()
                .productId(productId)
                .quantity(quantity)
                .reserved(0)
                .build();
        inventoryRepository.save(inv);
        auditService.log("STOCK_INITIALIZED", "Inventory", productId, "qty=" + quantity);
        log.info("Stock initialized productId={} qty={}", productId, quantity);
        return InventoryView.from(inv);
    }

    @Transactional
    public void reserve(Long productId, int quantity) {
        applyChaosOnReserve();
        Inventory inv = findLocked(productId);
        if (!inv.canReserve(quantity)) {
            throw new InsufficientStockException(productId, inv.availableQuantity(), quantity);
        }
        inv.reserve(quantity);
        inventoryRepository.save(inv);
        auditService.log("STOCK_RESERVED", "Inventory", productId,
                "qty=" + quantity + " remaining=" + inv.availableQuantity());
        log.info("Stock reserved productId={} qty={} available={}", productId, quantity, inv.availableQuantity());
    }

    @Transactional
    public void release(Long productId, int quantity) {
        Inventory inv = findLocked(productId);
        inv.release(quantity);
        inventoryRepository.save(inv);
        auditService.log("STOCK_RELEASED", "Inventory", productId, "qty=" + quantity);
        log.info("Stock released productId={} qty={}", productId, quantity);
    }

    @Transactional
    public void deduct(Long productId, int quantity) {
        Inventory inv = findLocked(productId);
        inv.deduct(quantity);
        inventoryRepository.save(inv);
        auditService.log("STOCK_DEDUCTED", "Inventory", productId,
                "qty=" + quantity + " remaining=" + inv.availableQuantity());
    }

    @Transactional
    public void addStock(Long productId, int quantity) {
        Inventory inv = findLocked(productId);
        inv.setQuantity(inv.getQuantity() + quantity);
        inventoryRepository.save(inv);
        auditService.log("STOCK_ADDED", "Inventory", productId,
                "added=" + quantity + " total=" + inv.getQuantity());
        log.info("Stock added productId={} qty={} total={}", productId, quantity, inv.getQuantity());
    }

    private Inventory find(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new NotFoundException("Inventory not found for product: " + productId));
    }

    private Inventory findLocked(Long productId) {
        return inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new NotFoundException("Inventory not found for product: " + productId));
    }
}
