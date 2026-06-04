package com.shopwave.inventory.dto;

import com.shopwave.inventory.domain.Inventory;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data @Builder
public class InventoryView {
    private Long id;
    private Long productId;
    private int quantity;
    private int reserved;
    private int available;
    private OffsetDateTime updatedAt;

    public static InventoryView from(Inventory inv) {
        return InventoryView.builder()
                .id(inv.getId())
                .productId(inv.getProductId())
                .quantity(inv.getQuantity())
                .reserved(inv.getReserved())
                .available(inv.availableQuantity())
                .updatedAt(inv.getUpdatedAt())
                .build();
    }
}
