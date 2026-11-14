package com.phucle.ecommerce.inventory.application;

import com.phucle.ecommerce.identity.api.TrustedActor;
import java.util.UUID;

public interface SellerStockCommands {
    record Update(int onHand, long expectedVersion) {}
    record Stock(UUID variantId, int onHand, long version) {}
    Stock replace(TrustedActor actor, UUID variantId, Update update);
}
