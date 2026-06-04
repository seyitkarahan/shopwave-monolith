package com.shopwave.client;

import java.util.List;
import java.util.Map;

public interface InventoryClient {

    InventoryView getByProductId(Long productId);

    List<InventoryView> getLowStock(int threshold);

    void initStock(Long productId, int quantity);

    void reserve(Long productId, int quantity);

    void release(Long productId, int quantity);

    void deduct(Long productId, int quantity);

    Map<String, String> addStock(Long productId, int quantity);
}
