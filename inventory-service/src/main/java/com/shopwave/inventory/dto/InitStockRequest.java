package com.shopwave.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InitStockRequest {
    @NotNull
    private Long productId;

    @Min(0)
    private int quantity;
}
