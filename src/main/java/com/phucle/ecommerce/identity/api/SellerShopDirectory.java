package com.phucle.ecommerce.identity.api;

import java.util.Optional;
import java.util.UUID;

/** Trusted mapping from an authenticated Seller account to its shop identity. */
public interface SellerShopDirectory {
    Optional<UUID> shopIdForAccount(UUID accountId);
}
