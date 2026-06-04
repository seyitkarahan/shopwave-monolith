package com.shopwave.client;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class InventoryView {
    private Long id;
    private Long productId;
    private int quantity;
    private int reserved;
    private int available;
    private OffsetDateTime updatedAt;
}
