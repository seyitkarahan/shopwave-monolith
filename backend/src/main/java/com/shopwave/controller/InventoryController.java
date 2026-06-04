package com.shopwave.controller;

import com.shopwave.client.InventoryClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Monolith proxy — frontend hâlâ :8080 üzerinden stok API'sine erişir. */
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryClient inventoryClient;

    @GetMapping("/products/{productId}")
    public Object getByProduct(@PathVariable Long productId) {
        return inventoryClient.getByProductId(productId);
    }

    @GetMapping("/low-stock")
    public Object lowStock(@RequestParam(defaultValue = "10") int threshold) {
        return inventoryClient.getLowStock(threshold);
    }

    @PostMapping("/products/{productId}/add")
    public Object addStock(@PathVariable Long productId,
                           @RequestBody Map<String, Integer> body) {
        return inventoryClient.addStock(productId, body.get("quantity"));
    }
}
