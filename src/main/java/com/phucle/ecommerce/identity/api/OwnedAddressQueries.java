package com.phucle.ecommerce.identity.api;

import java.util.List;
import java.util.UUID;

public interface OwnedAddressQueries {
    Page list(TrustedActor actor, String cursor, int limit);
    AddressView findOwned(TrustedActor actor, UUID addressId);
    record Page(List<AddressView> items, String nextCursor, boolean hasNext) {
        public Page { items = List.copyOf(items); }
    }
}
