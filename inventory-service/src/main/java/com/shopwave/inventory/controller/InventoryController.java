package com.shopwave.inventory.controller;

import com.shopwave.inventory.dto.InitStockRequest;
import com.shopwave.inventory.dto.InventoryView;
import com.shopwave.inventory.dto.StockOperationRequest;
import com.shopwave.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/products/{productId}")
    public InventoryView getByProduct(@PathVariable Long productId) {
        return inventoryService.getByProductId(productId);
    }

    @GetMapping("/low-stock")
    public Object lowStock(@RequestParam(defaultValue = "10") int threshold) {
        return inventoryService.getLowStock(threshold);
    }

    @PostMapping("/products/init")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryView initStock(@Valid @RequestBody InitStockRequest req) {
        return inventoryService.initStock(req.getProductId(), req.getQuantity());
    }

    @PostMapping("/reserve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reserve(@Valid @RequestBody StockOperationRequest req) {
        inventoryService.reserve(req.getProductId(), req.getQuantity());
    }

    @PostMapping("/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@Valid @RequestBody StockOperationRequest req) {
        inventoryService.release(req.getProductId(), req.getQuantity());
    }

    @PostMapping("/deduct")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deduct(@Valid @RequestBody StockOperationRequest req) {
        inventoryService.deduct(req.getProductId(), req.getQuantity());
    }

    @PostMapping("/products/{productId}/add")
    public Map<String, String> addStock(@PathVariable Long productId,
                                        @RequestBody Map<String, Integer> body) {
        inventoryService.addStock(productId, body.get("quantity"));
        return Map.of("message", "Stock added");
    }
}
