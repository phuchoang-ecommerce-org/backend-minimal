package com.phucle.ecommerce.inventory.application;

import com.phucle.ecommerce.inventory.domain.StockItem;
import java.util.UUID;

public interface StockWritePort {
    StockItem find(UUID variantId);
    StockItem replace(StockItem previous, StockItem updated);
}
