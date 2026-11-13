package com.phucle.ecommerce.inventory.api;

import java.util.Map;
import java.util.UUID;

public interface CheckoutStock {
    boolean decrease(Map<UUID, Integer> quantities);
}
